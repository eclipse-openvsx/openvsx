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

import java.nio.file.Files;

import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.util.NamingUtil;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class RenameDownloadsJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {

    protected final Logger logger = LoggerFactory.getLogger(RenameDownloadsJobRequestHandler.class);

    private final MigrationService migrations;
    private final RenameDownloadsService service;

    public RenameDownloadsJobRequestHandler(MigrationService migrations, RenameDownloadsService service) {
        this.migrations = migrations;
        this.service = service;
    }

    @Override
    public void run(MigrationJobRequest jobRequest) throws Exception {
        var download = migrations.getResource(jobRequest);
        var name = NamingUtil.toFileFormat(download.getExtension(), ".vsix");
        if (download.getName().equals(name)) {
            // names are the same, nothing to do
            return;
        }

        logger.info("Renaming download {}", download.getName());
        try (var extensionFile = migrations.getExtensionFile(download)) {
            if (Files.size(extensionFile.getPath()) == 0) {
                return;
            }

            var newDownload = service.cloneResource(download, name);
            extensionFile.setResource(newDownload);
            migrations.uploadFileResource(extensionFile);
            migrations.removeFile(download);

            download.setName(name);
            service.updateResourceName(download);
        }

        logger.info("Updated download name to: {}", name);
    }
}
