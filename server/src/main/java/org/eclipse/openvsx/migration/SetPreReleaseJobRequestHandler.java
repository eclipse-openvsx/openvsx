/******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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
package org.eclipse.openvsx.migration;

import java.nio.file.Files;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class SetPreReleaseJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {

    protected final Logger logger = new JobRunrDashboardLogger(
            LoggerFactory.getLogger(SetPreReleaseJobRequestHandler.class));

    private final MigrationService migrations;
    private final SetPreReleaseJobService service;

    public SetPreReleaseJobRequestHandler(MigrationService migrations, SetPreReleaseJobService service) {
        this.migrations = migrations;
        this.service = service;
    }

    @Override
    @Job(name = "Set pre-release and preview for published extensions", retries = 3)
    public void run(MigrationJobRequest jobRequest) throws Exception {
        var extVersions = service.getExtensionVersions(jobRequest, logger);
        for (var extVersion : extVersions) {
            var download = migrations.getDownload(extVersion);
            if (download != null) {
                try (var extensionFile = migrations.getExtensionFile(download)) {
                    if (Files.size(extensionFile.getPath()) > 0) {
                        service.updatePreviewAndPreRelease(extVersion, extensionFile);
                    }
                }
            }
        }
    }
}
