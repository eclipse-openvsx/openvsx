/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.NamingUtil;

import static org.eclipse.openvsx.entities.FileResource.*;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class FixMissingFilesJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {
    protected final Logger logger = new JobRunrDashboardLogger(
            LoggerFactory.getLogger(FixMissingFilesJobRequestHandler.class));

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storage;

    public FixMissingFilesJobRequestHandler(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storage
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storage = storage;
    }

    @Override
    public void run(MigrationJobRequest request) throws Exception {
        var extVersion = entityManager.find(ExtensionVersion.class, request.getEntityId());
        var resources = repositories.findFiles(extVersion);
        var resourceTypes = resources.map(FileResource::getType).toList();
        var missingFileTypes = new ArrayList<>(List.of(MANIFEST, CHANGELOG, README, LICENSE, ICON, VSIXMANIFEST));
        missingFileTypes.removeAll(resourceTypes);
        resources.filter(this::isFileMissing).map(FileResource::getType).forEach(missingFileTypes::add);
        if (missingFileTypes.isEmpty()) {
            return;
        }
        var download = resources.stream().filter(f -> f.getType().equals(FileResource.DOWNLOAD)).findFirst();
        if (download.isEmpty() || missingFileTypes.contains(FileResource.DOWNLOAD)) {
            logger.atInfo()
                    .setMessage("No vsix package available for: {}")
                    .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                    .log();
            return;
        }

        logger.atInfo()
                .setMessage("{} has missing files: {}")
                .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                .addArgument(() -> String.join(",", missingFileTypes))
                .log();
        try (
                var tempFile = storage.downloadFile(download.get());
                var processor = new ExtensionProcessor(tempFile)
        ) {
            processor.getFileResources(extVersion, (file) -> {
                if (missingFileTypes.contains(file.getResource().getType())) {
                    storage.uploadFile(file);
                    logger.atInfo()
                            .setMessage("Uploaded {} file for: {}")
                            .addArgument(() -> file.getResource().getType())
                            .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                            .log();
                }
            });
        }
    }

    private boolean isFileMissing(FileResource resource) {
        try (var tempFile = storage.downloadFile(resource)) {
            return Files.size(tempFile.getPath()) == 0;
        } catch (Exception e) {
            return true;
        }
    }
}
