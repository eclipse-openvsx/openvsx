/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.migration;

import java.io.IOException;
import java.nio.file.Files;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.util.NamingUtil;

@Component
public class GenerateSha256ChecksumJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {

    protected final Logger logger = new JobRunrDashboardLogger(
            LoggerFactory.getLogger(GenerateSha256ChecksumJobRequestHandler.class));

    private final MigrationService migrations;

    public GenerateSha256ChecksumJobRequestHandler(MigrationService migrations) {
        this.migrations = migrations;
    }

    @Override
    @Job(name = "Generate sha256 checksum for published extension version", retries = 3)
    public void run(MigrationJobRequest jobRequest) throws IOException {
        var download = migrations.getResource(jobRequest);
        var extVersion = download.getExtension();
        logger.atInfo()
                .setMessage("Generate sha256 checksum for: {}")
                .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                .log();

        var existingChecksum = migrations.getFileResource(extVersion, FileResource.DOWNLOAD_SHA256);
        if (existingChecksum != null) {
            migrations.removeFile(existingChecksum);
            migrations.deleteFileResource(existingChecksum);
        }

        try (var extensionFile = migrations.getExtensionFile(download)) {
            if (Files.size(extensionFile.getPath()) == 0) {
                return;
            }
            try (
                    var extProcessor = new ExtensionProcessor(extensionFile);
                    var checksumFile = extProcessor.generateSha256Checksum(extVersion)
            ) {
                migrations.uploadFileResource(checksumFile);
                var resource = checksumFile.getResource();
                resource.setStorageType(download.getStorageType());
                migrations.persistFileResource(resource);
            }
        }
    }
}
