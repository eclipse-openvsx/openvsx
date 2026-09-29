/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.migration;

import java.io.IOException;
import java.nio.file.Files;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class ExtensionVersionSignatureJobRequestHandler implements JobRequestHandler<MigrationJobRequest<?>> {

    protected final Logger logger = new JobRunrDashboardLogger(
            LoggerFactory.getLogger(ExtensionVersionSignatureJobRequestHandler.class));

    private final RepositoryService repositories;
    private final CacheService cache;
    private final MigrationService migrations;
    private final ExtensionVersionIntegrityService integrityService;

    public ExtensionVersionSignatureJobRequestHandler(
            RepositoryService repositories,
            CacheService cache,
            MigrationService migrations,
            ExtensionVersionIntegrityService integrityService
    ) {
        this.repositories = repositories;
        this.cache = cache;
        this.migrations = migrations;
        this.integrityService = integrityService;
    }

    @Override
    @Job(name = "Generate signature for extension version", retries = 3)
    public void run(MigrationJobRequest jobRequest) throws IOException {
        var extVersion = migrations.getExtension(jobRequest.getEntityId());
        if (extVersion == null) {
            return;
        }

        var download = migrations.getDownload(extVersion);
        if (download == null) {
            return;
        }

        logger.atInfo()
                .setMessage("Generating signature for: {}")
                .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                .log();

        var keyPair = repositories.findActiveKeyPair();
        try (var signatureFile = createSignature(download, keyPair)) {
            if (signatureFile == null) {
                return;
            }

            integrityService.setSignatureKeyPair(extVersion, keyPair);
            var extension = extVersion.getExtension();
            cache.evictExtensionJsons(extVersion);
            cache.evictLatestExtensionVersion(extension);
            cache.evictNamespaceDetails(extension);

            var existingSignature = migrations.getFileResource(extVersion, FileResource.DOWNLOAD_SIG);
            if (existingSignature != null) {
                migrations.removeFile(existingSignature);
                migrations.deleteFileResource(existingSignature);
            }

            migrations.uploadFileResource(signatureFile);
            migrations.persistFileResource(signatureFile.getResource());
        }
    }

    private TempFile createSignature(FileResource download, SignatureKeyPair keyPair) throws IOException {
        try (var extensionFile = migrations.getExtensionFile(download)) {
            if (Files.size(extensionFile.getPath()) == 0) {
                return null;
            }

            var signatureFile = integrityService.generateSignature(extensionFile, keyPair);
            signatureFile.getResource().setStorageType(download.getStorageType());
            return signatureFile;
        }
    }
}
