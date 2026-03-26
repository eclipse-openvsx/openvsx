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

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.TempFile;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import static org.eclipse.openvsx.cache.CacheService.CACHE_SITEMAP;

@Component
public class PublishExtensionVersionService {

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

    @Retryable
    public void storeResource(TempFile tempFile) {
        storageUtil.uploadFile(tempFile);
    }

    @Transactional
    public void mirrorResource(TempFile tempFile) {
        mirrorResource(tempFile.getResource());
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
        extVersion = entityManager.merge(extVersion);
        extVersion.setPotentiallyMalicious(true);
    }

    @Transactional
    @CacheEvict(value = CACHE_SITEMAP, allEntries = true)
    public void activateExtension(ExtensionVersion extVersion, ExtensionService extensions) {
        extVersion.setActive(true);
        extVersion = entityManager.merge(extVersion);
        extensions.updateExtension(extVersion.getExtension());
    }
}
