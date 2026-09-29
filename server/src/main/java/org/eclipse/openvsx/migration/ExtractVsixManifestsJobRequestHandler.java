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

import java.nio.file.Files;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.util.NamingUtil;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class ExtractVsixManifestsJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {

    protected final Logger logger = new JobRunrDashboardLogger(
            LoggerFactory.getLogger(ExtractVsixManifestsJobRequestHandler.class));

    private final MigrationService migrations;

    public ExtractVsixManifestsJobRequestHandler(MigrationService migrations) {
        this.migrations = migrations;
    }

    @Override
    @Job(name = "Extract VSIX manifests from published extension version", retries = 3)
    public void run(MigrationJobRequest jobRequest) throws Exception {
        var download = migrations.getResource(jobRequest);
        var extVersion = download.getExtension();
        logger.atInfo()
                .setMessage("Extracting VSIX manifests for: {}")
                .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                .log();

        var existingVsixManifest = migrations.getFileResource(extVersion, FileResource.VSIXMANIFEST);
        if (existingVsixManifest != null) {
            migrations.removeFile(existingVsixManifest);
            migrations.deleteFileResource(existingVsixManifest);
        }

        try (var extensionFile = migrations.getExtensionFile(download)) {
            if (Files.size(extensionFile.getPath()) == 0) {
                return;
            }
            try (
                    var extProcessor = new ExtensionProcessor(extensionFile);
                    var vsixManifestFile = extProcessor.getVsixManifest(extVersion)
            ) {
                migrations.uploadFileResource(vsixManifestFile);
                var resource = vsixManifestFile.getResource();
                resource.setStorageType(download.getStorageType());
                migrations.persistFileResource(resource);
            }
        }
    }
}
