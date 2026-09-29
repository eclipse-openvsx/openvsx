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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.cache.jedis.JedisClusterChannelListener;

/**
 * Tells the other nodes that their settings cache is stale. The payload is never read - the message
 * itself is the signal. Without a Redis cluster there is nothing to tell, so every method is a no-op
 * and the subscriber never starts.
 */
@Component
public class SettingsUpdateChannel {

    private static final String SETTINGS_UPDATE_CHANNEL = "settings.update";

    private final Logger logger = LoggerFactory.getLogger(SettingsUpdateChannel.class);

    private final @Nullable RedisClusterClient redisClusterClient;
    private final @Nullable SettingsUpdateListener listener;
    private final SettingsCache cache;

    public SettingsUpdateChannel(@Nullable RedisClusterClient redisClusterClient, SettingsCache cache) {
        this.redisClusterClient = redisClusterClient;
        this.cache = cache;

        if (redisClusterClient != null) {
            listener = new SettingsUpdateListener(redisClusterClient);
            logger.info("Settings update listener initialized");
        } else {
            listener = null;
        }
    }

    @PostConstruct
    public void initialize() {
        if (listener != null) {
            listener.startSubscriber();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (listener != null) {
            listener.shutdown();
        }
    }

    /** Once per save rather than once per row, or the other nodes reload a store mid-write. */
    public void publish() {
        if (redisClusterClient != null) {
            logger.debug("Publish settings update");
            redisClusterClient.publish(SETTINGS_UPDATE_CHANNEL, String.valueOf(System.currentTimeMillis()));
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
