/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.settings;

import java.util.ArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.cache.jedis.JedisClusterChannelListener;
import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.util.AfterCommitExecutor;
import org.eclipse.openvsx.util.ErrorResultException;

@Service
public class SettingsService {

    public static final String SETTING_REGISTRY_READ_ONLY = "read-only";
    public static final String SETTING_MAX_EXTENSION_SIZE = "max-extension-size";
    private static final String SETTINGS_UPDATE_CHANNEL = "settings.update";

    private final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final @Nullable RedisClusterClient redisClusterClient;
    private final SettingsUpdateListener settingsUpdateListener;
    private final SettingsCache cache;
    private final PublishingConfig publishingConfig;
    private final AfterCommitExecutor afterCommit;

    public SettingsService(
            @Nullable RedisClusterClient redisClusterClient,
            SettingsCache cache,
            PublishingConfig publishingConfig,
            AfterCommitExecutor afterCommit
    ) {
        this.redisClusterClient = redisClusterClient;
        this.cache = cache;
        this.publishingConfig = publishingConfig;
        this.afterCommit = afterCommit;

        if (redisClusterClient != null) {
            settingsUpdateListener = new SettingsUpdateListener(redisClusterClient);
            logger.info("SettingsService initialized with Redis update listener");
        } else {
            settingsUpdateListener = null;
        }
    }

    @PostConstruct
    public void initialize() {
        if (settingsUpdateListener != null) {
            settingsUpdateListener.startSubscriber();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (settingsUpdateListener != null) {
            settingsUpdateListener.shutdown();
        }
    }

    public boolean isReadOnly() {
        return cache.getBoolean(SETTING_REGISTRY_READ_ONLY, false);
    }

    public long getMaxExtensionSize() {
        return cache.getLong(SETTING_MAX_EXTENSION_SIZE, publishingConfig.getMaxContentSize());
    }

    public SettingsJson getCurrentSettings() {
        var json = new SettingsJson();
        json.setReadOnly(isReadOnly());
        json.setMaxExtensionSize(getMaxExtensionSize());
        json.setMaxOverrideSize(publishingConfig.getMaxOverrideSize());
        return json;
    }

    /**
     * Applies the settings the request carries and leaves out every other one alone. A client that
     * does not know about a setting - a stale admin tab, an older integration - must not reset it by
     * omitting it.
     */
    public String updateFromJson(SettingsJson newSettings) {
        var readOnly = newSettings.getReadOnly();
        var maxExtensionSize = newSettings.getMaxExtensionSize();
        if (maxExtensionSize != null) {
            if (maxExtensionSize <= 0) {
                throw new ErrorResultException("Max extension size must be greater than zero.");
            }
            var ceiling = publishingConfig.getMaxOverrideSize();
            if (maxExtensionSize > ceiling) {
                throw new ErrorResultException("Max extension size exceeds the maximum of " + ceiling + " bytes.");
            }
        }

        // Every setting the request carries is written, even one that matches what is read back now.
        // The comparison would be against a node-local cache entry that can be a minute stale, or
        // against the configuration fallback on a registry where nothing has been stored yet - so
        // skipping the write dropped real changes: restoring a value another node had just moved
        // away from, and storing the setting that is meant to shadow the configuration file. The
        // upsert is idempotent, so writing unconditionally costs only the statement.
        var changes = new ArrayList<>();
        if (readOnly != null) {
            changes.add("readOnly -> " + readOnly);
            cache.setBoolean(SETTING_REGISTRY_READ_ONLY, readOnly);
        }
        if (maxExtensionSize != null) {
            changes.add("maxExtensionSize -> " + maxExtensionSize);
            cache.setLong(SETTING_MAX_EXTENSION_SIZE, maxExtensionSize);
            // The derived size ceiling is cached under its own key, so evict the whole settings
            // cache rather than just this one entry.
            cache.clear();
        }
        publishSettingsUpdate();
        return Strings.join(changes, ',');
    }

    /**
     * Drop every cached setting on this node and tell the other nodes to do the same. Callers that
     * change data the settings cache derives from — notably the size ceiling, which is cached under its
     * own key — must call this; evicting a single key is not enough.
     * <p>
     * Deferred to after the commit: these callers change rows the ceiling is derived from, and a clear
     * issued before their commit lets any node refill the ceiling from the rows as they still are.
     */
    public void invalidateCache() {
        afterCommit.execute(() -> {
            cache.clear();
            publishSettingsUpdate();
        });
    }

    private void publishSettingsUpdate() {
        if (redisClusterClient != null) {
            logger.debug("Publish settings update");
            String version = String.valueOf(System.currentTimeMillis());
            redisClusterClient.publish(SETTINGS_UPDATE_CHANNEL, version);
        }
    }

    private class SettingsUpdateListener extends JedisClusterChannelListener {
        SettingsUpdateListener(RedisClusterClient redisClusterClient) {
            super(redisClusterClient, SETTINGS_UPDATE_CHANNEL, "SettingsUpdate");
        }

        @Override
        public void onMessage(String channel, String message) {
            if (SETTINGS_UPDATE_CHANNEL.equals(channel)) {
                logger.debug("received settings update");
                cache.clear();
            }
        }
    }
}
