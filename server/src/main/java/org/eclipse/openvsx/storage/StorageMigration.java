/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.eclipse.openvsx.entities.FileResource.*;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(value = "ovsx.storage.migration.enabled", havingValue = "true", matchIfMissing = true)
public class StorageMigration {

    protected final Logger logger = LoggerFactory.getLogger(StorageMigration.class);

    private final TaskScheduler taskScheduler;
    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final ConcurrentLinkedQueue<Long> resourceQueue;
    private ScheduledFuture<?> scheduledFuture;

    @Value("${ovsx.storage.migration-delay:500}")
    long migrationDelay;

    public StorageMigration(
            TaskScheduler taskScheduler,
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil
    ) {
        this.taskScheduler = taskScheduler;
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.resourceQueue = new ConcurrentLinkedQueue<>();
    }

    @EventListener
    public void findResources(ApplicationStartedEvent event) {
        var storageType = storageUtil.getActiveStorageType();
        if (storageType.equals(STORAGE_LOCAL)) {
            // No migration is performed if we store resources locally
            return;
        }

        var migrations = new ArrayList<>(List.of(STORAGE_LOCAL, STORAGE_GOOGLE, STORAGE_AZURE, STORAGE_AWS));
        migrations.remove(storageType);
        var migrationCount = new int[migrations.size()];
        for (var i = 0; i < migrations.size(); i++) {
            final int index = i;
            repositories.findFilesByStorageType(migrations.get(index))
                    .filter(
                            resource -> !resource.getStorageType().equals(STORAGE_LOCAL)
                                    || storageUtil.shouldStoreExternally(resource))
                    .forEach(resource -> {
                        resourceQueue.add(resource.getId());
                        migrationCount[index]++;
                    });
        }

        if (!resourceQueue.isEmpty()) {
            for (var i = 0; i < migrations.size(); i++) {
                if (migrationCount[i] > 0) {
                    var index = i;
                    logger.atInfo()
                            .setMessage("Migrating {} resources from {} to {}.")
                            .addArgument(() -> migrationCount[index])
                            .addArgument(() -> migrations.get(index))
                            .addArgument(() -> storageType)
                            .log();
                }
            }
            var duration = Duration.of(migrationDelay, ChronoUnit.MILLIS);
            scheduledFuture = taskScheduler.scheduleWithFixedDelay(this::migrateResources, duration);
        }
    }

    public void migrateResources() {
        var resourceId = resourceQueue.poll();
        if (resourceId == null) {
            logger.info("Completed migration of resources.");
            scheduledFuture.cancel(false);
            return;
        }

        var resource = entityManager.find(FileResource.class, resourceId);
        if (resource == null) {
            return;
        }

        try (var extensionFile = storageUtil.downloadFile(resource)) {
            storageUtil.uploadFile(extensionFile);
        } catch (IOException e) {
            logger.error("Failed to migrate resource", e);
            return;
        }

        var remainingCount = resourceQueue.size();
        if (remainingCount > 0 && remainingCount % 1000 == 0) {
            logger.info("Remaining resources to migrate: {}", remainingCount);
        }
    }
}
