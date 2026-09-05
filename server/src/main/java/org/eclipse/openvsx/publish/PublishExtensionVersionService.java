/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import java.io.IOException;
import java.nio.file.Files;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.TimeUtil;

import static org.eclipse.openvsx.cache.CacheService.CACHE_SITEMAP;

@Component
public class PublishExtensionVersionService {

    private static final Logger logger = LoggerFactory.getLogger(PublishExtensionVersionService.class);

    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final StorageUtilService storageUtil;

    public PublishExtensionVersionService(
            RepositoryService repositories,
            EntityManager entityManager,
            StorageUtilService storageUtil
    ) {
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.storageUtil = storageUtil;
    }

    @Transactional
    public void deleteFileResources(ExtensionVersion extVersion) {
        repositories.deleteFiles(extVersion);
    }

    /**
     * Writes down why the publish of a version did not finish, so that a row left at
     * {@code active == false} can account for itself.
     * <p>
     * Its own transaction, and the only column it touches, because the transaction the failure happened
     * in is on its way to being rolled back: recording the reason must not be rolled back with it.
     * <p>
     * find-then-set rather than merge, per the rule the rest of this class follows: the version handed in
     * has been through a failed attempt and may carry a stale snapshot of every other column.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordPublishError(ExtensionVersion extVersion, String reason) {
        var current = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (current == null) {
            // Nothing left to annotate; the row was purged while the attempt was running.
            return;
        }

        current.setPublishError(reason);
    }

    @Retryable
    public void storeResource(TempFile tempFile) {
        storageUtil.uploadFile(tempFile);
    }

    @Transactional
    public void mirrorResource(TempFile tempFile) {
        var resource = tempFile.getResource();
        try {
            // the bytes were extracted from the mirrored package to build this TempFile, even though
            // they aren't uploaded to storage here (mirror mode serves resources on the fly), so the
            // size is still known and worth recording.
            resource.setSize(Files.size(tempFile.getPath()));
        } catch (IOException e) {
            throw new ServerErrorException("Failed to determine file size", e);
        }

        mirrorResource(resource);
    }

    @Transactional
    public void mirrorResource(FileResource resource) {
        resource.setStorageType(storageUtil.getActiveStorageType());
        entityManager.persist(resource);
    }

    @Transactional
    public void persistResource(FileResource resource) {
        entityManager.persist(resource);
    }

    @Transactional
    public void markExtensionAsPotentiallyMalicious(ExtensionVersion extVersion) {
        // find-then-set rather than merge: merge writes every column of the detached copy, so
        // anything that changed on the row since it was loaded gets reverted. Only the field
        // below is this method's to change - see #989.
        var managedVersion = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (managedVersion == null) {
            return;
        }
        managedVersion.setPotentiallyMalicious(true);
    }

    @Transactional
    @CacheEvict(value = CACHE_SITEMAP, allEntries = true)
    public void activateExtension(ExtensionVersion extVersion, ExtensionService extensions) {
        // Reload the current row before mutating: the passed-in entity may carry a stale snapshot (e.g. it
        // was fetched before a concurrent soft-delete committed), so we must not trust its flags. This
        // matters when a version is soft-deleted while an asynchronous scan for it is still in flight: the
        // scan completing (or being allowed by an admin) must not resurrect the removed version, whose files
        // have already been stripped from storage.
        var current = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (current == null) {
            // The row was purged (hard-deleted) in the meantime; nothing to activate.
            logger.warn("Refusing to activate missing extension version: {}", NamingUtil.toLogFormat(extVersion));
            return;
        }

        // A soft-deleted version is a permanent tombstone and must never be reactivated.
        if (current.isRemoved()) {
            logger.warn("Refusing to activate removed extension version: {}", NamingUtil.toLogFormat(current));
            return;
        }

        // Activating a version that is already active changes nothing publicly, and the changes feed
        // log is append-only: recording it again would report a second publication that never happened.
        var alreadyActive = current.isActive();
        current.setActive(true);
        // Whatever a previous attempt failed on has been superseded by the one that got here.
        current.setPublishError(null);
        if (!alreadyActive) {
            // The version becomes publicly visible here, which is what the changes feed reports as its
            // publication. It is reported at the current instant rather than at the timestamp the version
            // carries: the two are far apart whenever activation waited on something, such as a scan
            // completing or an admin releasing a quarantined version days later, and an entry written at
            // the older instant would sort into a part of the feed that consumers have already read past
            // -- they would never see the version get published. The version's own timestamp is reported
            // separately on every entry, so nothing is lost by not ordering on it.
            repositories.recordExtensionVersionChange(
                    current,
                    ExtensionVersionState.ACTIVE,
                    TimeUtil.getCurrentUTC());
        }
        extensions.updateExtension(current.getExtension());
    }
}
