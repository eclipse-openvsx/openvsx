/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.extension_control;

import java.util.ArrayList;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.SettingsService;
import org.eclipse.openvsx.util.NamingUtil;

@Component
public class ExtensionControlJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    protected final Logger logger = LoggerFactory.getLogger(ExtensionControlJobRequestHandler.class);

    private final SettingsService settings;
    private final AdminService admin;
    private final ExtensionControlService service;
    private final RepositoryService repositories;

    public ExtensionControlJobRequestHandler(
            SettingsService settings,
            AdminService admin,
            ExtensionControlService service,
            RepositoryService repositories
    ) {
        this.settings = settings;
        this.admin = admin;
        this.service = service;
        this.repositories = repositories;
    }

    @Override
    @Job(name = "Extension control update", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (settings.isReadOnly()) {
            return;
        }

        logger.info("Run extension control job");
        var json = service.getExtensionControlJson();
        logger.info("Got extension control JSON");
        processMaliciousExtensions(json);
        processDeprecatedExtensions(json);
    }

    private void processMaliciousExtensions(JsonNode json) {
        logger.info("Process malicious extensions");
        var node = json.get("malicious");
        if (node == null || !node.isArray()) {
            logger.error("field 'malicious' is not an array");
            return;
        }

        var extensionControlUser = service.createExtensionControlUser();
        var maliciousExtensionIds = new ArrayList<String>();
        for (var item : node) {
            var itemId = item.asString();
            maliciousExtensionIds.add(itemId);
            logger.atInfo().setMessage("malicious: {}").addArgument(itemId).log();

            var extensionId = NamingUtil.fromExtensionId(itemId);
            if (extensionId != null && repositories.hasExtension(extensionId.namespace(), extensionId.extension())) {
                logger.info("delete malicious extension");
                if (service.deleteTransitively) {
                    admin.purgeExtensionAndReferencingExtensions(
                            extensionControlUser,
                            extensionId.namespace(),
                            extensionId.extension());
                } else {
                    admin.purgeExtension(extensionControlUser, extensionId.namespace(), extensionId.extension());
                }
            }
        }

        // This job always fetches a fresh copy; feed it straight into getMaliciousExtensionIds()'s cache
        // so the publish-time check reflects it immediately instead of drifting for up to that cache's
        // own, independent TTL.
        service.refreshMaliciousExtensionIds(maliciousExtensionIds);
    }

    private void processDeprecatedExtensions(JsonNode json) {
        logger.info("Process deprecated extensions");
        var node = json.get("deprecated");
        if (node == null || !node.isObject()) {
            logger.error("field 'deprecated' is not an object");
            return;
        }

        node.properties().iterator().forEachRemaining(field -> {
            logger.info("deprecated: {}", field.getKey());
            var extensionId = NamingUtil.fromExtensionId(field.getKey());
            if (extensionId == null) {
                return;
            }

            var value = field.getValue();
            if (value.isBoolean()) {
                service.updateExtension(extensionId, value.asBoolean(), null, true);
            } else if (value.isObject()) {
                var replacement = value.get("extension");
                var replacementId = replacement != null && replacement.isObject()
                        ? NamingUtil.fromExtensionId(replacement.get("id").asString())
                        : null;

                var disallowInstall = value.has("disallowInstall") && value.get("disallowInstall").asBoolean(false);
                service.updateExtension(extensionId, true, replacementId, !disallowInstall);
            } else {
                logger.error("field '{}' is not an object or a boolean", extensionId);
            }
        });
    }
}
