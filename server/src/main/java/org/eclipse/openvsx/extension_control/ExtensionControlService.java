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

import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.migration.MigrationsProperties;
import org.eclipse.openvsx.mirror.MirrorConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;

import static org.eclipse.openvsx.cache.CacheService.CACHE_MALICIOUS_EXTENSIONS;

@Component
public class ExtensionControlService {

    protected final Logger logger = LoggerFactory.getLogger(ExtensionControlService.class);

    private final JobRequestScheduler scheduler;
    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final SearchUtilService search;
    private final CacheService cache;
    private final MirrorConfig mirrorConfig;
    private final MigrationsProperties migrationsProperties;

    @Value("${ovsx.extension-control.enabled:true}")
    boolean enabled;

    @Value("${ovsx.extension-control.delete-transitively:false}")
    boolean deleteTransitively;

    @Value("${ovsx.extension-control.update-on-start:false}")
    boolean updateOnStart;

    public ExtensionControlService(
            JobRequestScheduler scheduler,
            RepositoryService repositories,
            EntityManager entityManager,
            SearchUtilService search,
            CacheService cache,
            MirrorConfig mirrorConfig,
            MigrationsProperties migrationsProperties
    ) {
        this.scheduler = scheduler;
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.search = search;
        this.cache = cache;
        this.mirrorConfig = mirrorConfig;
        this.migrationsProperties = migrationsProperties;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        if (!enabled || mirrorConfig.isEnabled()) {
            scheduler.deleteRecurringJob("UpdateExtensionControl");
        } else {
            if (updateOnStart) {
                scheduler.schedule(
                        TimeUtil.getCurrentUTC().plusSeconds(migrationsProperties.getDelaySeconds()),
                        new HandlerJobRequest<>(ExtensionControlJobRequestHandler.class));
            }

            var schedule = Cron.daily(1, 8);
            logger.info("Scheduling update extension control job with schedule '{}'", schedule);
            scheduler.scheduleRecurrently(
                    "UpdateExtensionControl",
                    schedule,
                    ZoneId.of("UTC"),
                    new HandlerJobRequest<>(ExtensionControlJobRequestHandler.class));
        }
    }

    @Transactional
    public UserData createExtensionControlUser() {
        var userName = "ExtensionControlUser";
        var user = repositories.findUserByLoginName("system", userName);
        if (user == null) {
            user = new UserData();
            user.setProvider("system");
            user.setLoginName(userName);
            entityManager.persist(user);
        }
        return user;
    }

    @Transactional
    public void updateExtension(
            ExtensionId extensionId,
            boolean deprecated,
            ExtensionId replacementId,
            boolean downloadable
    ) {
        var extension = repositories.findExtension(extensionId.extension(), extensionId.namespace());
        if (extension == null) {
            return;
        }

        var wasDeprecated = extension.isDeprecated();
        var oldReplacement = extension.getReplacement();
        extension.setDeprecated(deprecated);
        extension.setDownloadable(downloadable);
        if (replacementId != null) {
            var replacement = repositories.findExtension(replacementId.extension(), replacementId.namespace());
            if (replacement == null || !replacement.isActive()) {
                // Never point at a replacement that does not exist or has no active version; such a
                // pointer would surface a dead replacement link on the extension.
                if (replacement != null) {
                    logger.info(
                            "Ignoring inactive replacement {} configured for {}",
                            NamingUtil.toExtensionId(replacement),
                            NamingUtil.toExtensionId(extension));
                }
                extension.setReplacement(null);
            } else {
                extension.setReplacement(replacement);
            }
        }

        // The replacement is part of the (cached) extension JSON, so evict when it changes too, not
        // only when the deprecated flag flips. Compare by id, as the entity equals() is identity-based.
        var replacementChanged = !Objects.equals(
                oldReplacement != null ? oldReplacement.getId() : null,
                extension.getReplacement() != null ? extension.getReplacement().getId() : null);
        if (deprecated != wasDeprecated || replacementChanged) {
            cache.evictNamespaceDetails(extension);
            cache.evictLatestExtensionVersion(extension);
            cache.evictExtensionJsons(extension);
            search.updateSearchEntry(extension);
        }
    }

    public JsonNode getExtensionControlJson() throws IOException {
        var url = URI
                .create("https://github.com/open-vsx/publish-extensions/raw/master/extension-control/extensions.json")
                .toURL();
        try (var inputStream = url.openStream()) {
            return JsonMapper.shared().readValue(inputStream, JsonNode.class);
        }
    }

    @Cacheable(CACHE_MALICIOUS_EXTENSIONS)
    public List<String> getMaliciousExtensionIds() throws IOException {
        if (!enabled) {
            return Collections.emptyList();
        }

        var json = getExtensionControlJson();
        var malicious = json.get("malicious");
        if (!malicious.isArray()) {
            logger.error("field 'malicious' is not an array");
            return Collections.emptyList();
        }

        var list = new ArrayList<String>();
        malicious.forEach(node -> list.add(node.asString()));
        return list;
    }
}
