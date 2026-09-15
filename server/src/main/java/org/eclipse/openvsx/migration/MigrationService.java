/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Streamable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.MigrationItem;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.FileNotFoundInStorageException;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UUIDService;

@Component
public class MigrationService {

    private static final Map<String, Class<? extends JobRequestHandler<MigrationJobRequest<?>>>> JOB_HANDLERS = Map.of(
            "SetPreReleaseMigration",
            SetPreReleaseJobRequestHandler.class,
            "RenameDownloadsMigration",
            RenameDownloadsJobRequestHandler.class,
            "ExtractVsixManifestMigration",
            ExtractVsixManifestsJobRequestHandler.class,
            "GenerateSha256ChecksumMigration",
            GenerateSha256ChecksumJobRequestHandler.class,
            "CheckPotentiallyMaliciousExtensionVersions",
            PotentiallyMaliciousJobRequestHandler.class,
            "RemoveFileResourceTypeResourceMigration",
            RemoveFileResourceTypeResourceJobRequestHandler.class,
            "FixMissingFilesMigration",
            FixMissingFilesJobRequestHandler.class,
            "FileResourceSizeMigration",
            FileResourceSizeJobRequestHandler.class);

    protected final Logger logger = LoggerFactory.getLogger(MigrationService.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final JobRequestScheduler scheduler;
    private final UUIDService uuidService;

    public MigrationService(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil,
            JobRequestScheduler scheduler,
            UUIDService uuidService
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.scheduler = scheduler;
        this.uuidService = uuidService;
    }

    /**
     * Schedules {@code item}'s job to become due at {@code scheduledAt}, rather than immediately.
     * {@link MigrationItemJobRequestHandler} spreads a batch's items across a handful of seconds
     * instead of making them all due at once -- JobRunr processes every ENQUEUED/due job across the
     * whole server in one strict FIFO queue ordered by due time, so a large batch that's all due
     * "now" would sit ahead of any real, user-triggered job that happens to get enqueued around the
     * same time, delaying it until the whole batch drains.
     */
    @Transactional
    public void scheduleMigration(MigrationItem item, Instant scheduledAt) {
        // find rather than merge: only migrationScheduled below is this method's to write, and
        // merging the whole detached item reverted anything else that had moved - see #989.
        var itemId = item.getId();
        item = entityManager.find(MigrationItem.class, itemId);
        if (item == null) {
            // The row can be deleted between the caller reading its batch and this transaction
            // (MigrationItemCleanupFilter, or one of the Delete_MigrationItems migrations). Skip it
            // rather than dereferencing null, which would abort the rest of the batch.
            logger.debug("Migration item {} is gone, nothing to schedule", itemId);
            return;
        }
        var jobIdText = item.getJobName() + "->itemId=" + item.getId();
        var jobId = uuidService.generateFromName(jobIdText);
        var handler = JOB_HANDLERS.get(item.getJobName());
        scheduler.schedule(jobId, scheduledAt, new MigrationJobRequest<>(handler, item.getEntityId(), item.getId()));
        logger.info("Scheduled migration {} for {}", jobIdText, scheduledAt);
        item.setMigrationScheduled(true);
    }

    /**
     * Deletes the {@link MigrationItem} row identified by {@code id}, if it still exists. Called by
     * {@link MigrationItemCleanupFilter} once the job it scheduled has actually completed -- see that
     * class for why this isn't just done inline at the end of every job handler.
     */
    @Transactional
    public void deleteMigrationItem(long id) {
        var item = entityManager.find(MigrationItem.class, id);
        if (item != null) {
            entityManager.remove(item);
        }
    }

    public ExtensionVersion getExtension(long entityId) {
        return entityManager.find(ExtensionVersion.class, entityId);
    }

    public FileResource getResource(MigrationJobRequest<?> jobRequest) {
        return entityManager.find(FileResource.class, jobRequest.getEntityId());
    }

    /**
     * Persists the size its caller determined for {@code resource}, and nothing else. Named for the
     * one field it writes: a generic "update this resource" could only be implemented by merging the
     * whole detached copy, which reverts any column that changed since it was loaded (#989).
     */
    @Transactional
    public void updateResourceSize(FileResource resource) {
        var managedResource = entityManager.find(FileResource.class, resource.getId());
        if (managedResource == null) {
            return;
        }
        managedResource.setSize(resource.getSize());
    }

    @Retryable
    public TempFile getExtensionFile(FileResource resource) throws IOException {
        return storageUtil.downloadFile(resource);
    }

    // A FileNotFoundInStorageException means the object is definitively gone, not that this attempt
    // hit a transient error -- retrying it would just spend 3 more round trips (and the accompanying
    // delay) confirming the same absence, so it's excluded here and handled by the caller instead.
    @Retryable(excludes = FileNotFoundInStorageException.class)
    public long getFileSize(FileResource resource) throws IOException {
        return storageUtil.getFileSize(resource);
    }

    @Retryable
    public void uploadFileResource(TempFile tempFile) {
        storageUtil.uploadFile(tempFile);
    }

    @Retryable
    public void removeFile(FileResource resource) {
        storageUtil.removeFile(resource);
    }

    @Transactional
    public void persistFileResource(FileResource resource) {
        entityManager.persist(resource);
    }

    @Transactional
    public void deleteFileResource(FileResource resource) {
        var managedResource = entityManager.find(FileResource.class, resource.getId());
        if (managedResource != null) {
            entityManager.remove(managedResource);
        }
    }

    public FileResource getFileResource(ExtensionVersion extVersion, String type) {
        return repositories.findFileByType(extVersion, type);
    }

    public Streamable<FileResource> getFileResources(ExtensionVersion extVersion) {
        return repositories.findFiles(extVersion);
    }

    public FileResource getDownload(ExtensionVersion extVersion) {
        var download = repositories.findFileByType(extVersion, FileResource.DOWNLOAD);
        if (download == null) {
            logger.atWarn()
                    .setMessage("Could not find download for: {}")
                    .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                    .log();
        }

        return download;
    }
}
