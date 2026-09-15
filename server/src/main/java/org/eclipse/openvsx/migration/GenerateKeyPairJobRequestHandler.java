/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import java.io.IOException;

import io.micrometer.common.util.StringUtils;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.admin.RemoveFileJobRequest;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD_SIG;
import static org.eclipse.openvsx.entities.SignatureKeyPair.*;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class GenerateKeyPairJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(GenerateKeyPairJobRequestHandler.class);

    private final RepositoryService repositories;
    private final JobRequestScheduler scheduler;
    private final GenerateKeyPairJobService service;
    private final ExtensionVersionIntegrityService integrityService;

    public GenerateKeyPairJobRequestHandler(
            RepositoryService repositories,
            JobRequestScheduler scheduler,
            GenerateKeyPairJobService service,
            ExtensionVersionIntegrityService integrityService
    ) {
        this.repositories = repositories;
        this.scheduler = scheduler;
        this.service = service;
        this.integrityService = integrityService;
    }

    @Override
    @Job(retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        var keyPairMode = integrityService.getKeyPairMode();
        logger.info("Starting signature key-pair generation in mode {}", keyPairMode);

        switch (keyPairMode) {
            case KEYPAIR_MODE_CREATE:
                var activeKeyPair = repositories.findActiveKeyPair();
                if (activeKeyPair == null) {
                    renewKeyPair(true);
                } else {
                    repositories.findVersionsWithout(activeKeyPair).forEach(this::enqueueCreateSignatureJob);
                }
                break;
            case KEYPAIR_MODE_RENEW:
                renewKeyPair(false);
                break;
            case KEYPAIR_MODE_DELETE:
                deleteKeyPairs();
                logger.info("Existing signature key-pairs have been deleted");
                break;
            default :
                if (StringUtils.isNotEmpty(keyPairMode)) {
                    var values = String.join(",", KEYPAIR_MODE_CREATE, KEYPAIR_MODE_RENEW, KEYPAIR_MODE_DELETE);
                    throw new IllegalArgumentException(
                            "Unsupported value '" + keyPairMode
                                    + "' for 'ovsx.integrity.key-pair' defined. Supported values are: " + values);
                }
        }

        logger.info("Signature key-pair generation has been completed");
    }

    private void renewKeyPair(boolean create) throws IOException {
        var keyPair = service.generateKeyPair();
        try {
            service.updateKeyPair(keyPair);
            repositories.findVersions().forEach(this::enqueueCreateSignatureJob);

            if (create) {
                logger.info("Signature key-pair with id {} has been created", keyPair.getPublicId());
            } else {
                logger.info("Signature key-pair has been renewed, new id {}", keyPair.getPublicId());
            }
        } catch (DataAccessException ex) {
            logger.error("Failed to update signature key pair: {}", ex.getMessage());
        }
    }

    private void deleteKeyPairs() {
        repositories.findFilesByType(DOWNLOAD_SIG).forEach(this::enqueueDeleteSignatureJob);
        service.deleteSignaturesAndKeyPairs();
    }

    private void enqueueCreateSignatureJob(ExtensionVersion extVersion) {
        // Soft-deleted versions are tombstones whose files have been stripped from storage, so there is
        // nothing to sign. Skip them here rather than enqueuing a job that would find no download and no-op.
        if (extVersion.isRemoved()) {
            return;
        }

        var handler = ExtensionVersionSignatureJobRequestHandler.class;
        scheduler.enqueue(new MigrationJobRequest<>(handler, extVersion.getId()));
    }

    private void enqueueDeleteSignatureJob(FileResource resource) {
        scheduler.enqueue(new RemoveFileJobRequest(resource));
    }
}
