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

@Service
public class SettingsService {

    public static final String SETTING_REGISTRY_READ_ONLY = "read-only";
    private static final String SETTINGS_UPDATE_CHANNEL = "settings.update";

    private final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final @Nullable RedisClusterClient redisClusterClient;
    private final SettingsUpdateListener settingsUpdateListener;
    private final SettingsCache cache;

    public SettingsService(@Nullable RedisClusterClient redisClusterClient, SettingsCache cache) {
        this.redisClusterClient = redisClusterClient;
        this.cache = cache;

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

    public SettingsJson getCurrentSettings() {
        var json = new SettingsJson();
        json.setReadOnly(isReadOnly());
        return json;
    }

    public String updateFromJson(SettingsJson newSettings) {
        var changes = new ArrayList<>();
        if (newSettings.isReadOnly() != isReadOnly()) {
            changes.add("readOnly -> " + newSettings.isReadOnly());
            cache.setBoolean(SETTING_REGISTRY_READ_ONLY, newSettings.isReadOnly());
        }
        publishSettingsUpdate();
        return Strings.join(changes, ',');
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
