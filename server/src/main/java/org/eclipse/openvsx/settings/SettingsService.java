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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
    public static final String SETTING_BANNER_ENABLED = "banner-enabled";
    public static final String SETTING_BANNER_MESSAGE = "banner-message";
    public static final String SETTING_BANNER_SEVERITY = "banner-severity";
    public static final String SETTING_BANNER_DISMISS_ID = "banner-dismiss-id";

    static final String BANNER_KEY_PREFIX = "banner-";

    /** Kept out of the public settings endpoint; everything else in the store is served. */
    private static final Set<String> PRIVATE_SETTINGS = Set.of(SETTING_REGISTRY_READ_ONLY);

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
        return Boolean.TRUE.equals(cache.getAll().get(SETTING_REGISTRY_READ_ONLY));
    }

    public SettingsJson getCurrentSettings() {
        var stored = cache.getAll();
        var json = new SettingsJson();
        json.setReadOnly(Boolean.TRUE.equals(stored.get(SETTING_REGISTRY_READ_ONLY)));
        json.setBannerEnabled(Boolean.TRUE.equals(stored.get(SETTING_BANNER_ENABLED)));
        json.setBannerMessage(string(stored, SETTING_BANNER_MESSAGE));
        json.setBannerSeverity(stored.get(SETTING_BANNER_SEVERITY) instanceof String severity ? severity : "info");
        json.setBannerDismissId(string(stored, SETTING_BANNER_DISMISS_ID));
        return json;
    }

    /**
     * The settings the web UI may read. A denylist rather than an allowlist, so a new site setting
     * is served without another endpoint - which means anything that must stay internal has to be
     * added to {@link #PRIVATE_SETTINGS}.
     */
    public Map<String, Object> getSiteSettings() {
        var settings = new LinkedHashMap<>(cache.getAll());
        settings.keySet().removeAll(PRIVATE_SETTINGS);
        if (!Boolean.TRUE.equals(settings.get(SETTING_BANNER_ENABLED))) {
            // A banner drafted with the switch down isn't public yet.
            settings.keySet().removeIf(key -> key.startsWith(BANNER_KEY_PREFIX));
        }
        return settings;
    }

    public String updateFromJson(SettingsJson newSettings) {
        // Before any write, so a rejected value can't leave half of a save applied.
        SettingsValidator.validate(newSettings);

        var current = cache.getAll();
        var updates = toUpdates(newSettings);
        if (updates.keySet().stream().anyMatch(key -> key.startsWith(BANNER_KEY_PREFIX))) {
            updates.put(SETTING_BANNER_DISMISS_ID, dismissIdToStore(updates, current));
        }

        updates.entrySet().removeIf(entry -> isUnchanged(entry.getKey(), entry.getValue(), current));
        write(updates);
        publishSettingsUpdate();
        return describe(updates);
    }

    /** A setting the caller left null is one it isn't touching, so it never reaches the store. */
    private static Map<String, Object> toUpdates(SettingsJson newSettings) {
        var updates = new LinkedHashMap<String, Object>();
        putIfPresent(updates, SETTING_REGISTRY_READ_ONLY, newSettings.isReadOnly());
        putIfPresent(updates, SETTING_BANNER_ENABLED, newSettings.isBannerEnabled());
        putIfPresent(updates, SETTING_BANNER_MESSAGE, newSettings.getBannerMessage());
        putIfPresent(updates, SETTING_BANNER_SEVERITY, newSettings.getBannerSeverity());
        putIfPresent(updates, SETTING_BANNER_DISMISS_ID, newSettings.getBannerDismissId());
        return updates;
    }

    private static void putIfPresent(Map<String, Object> updates, String key, @Nullable Object value) {
        if (value != null) {
            updates.put(key, value);
        }
    }

    /** An empty value where nothing is stored is the same as leaving the setting unset. */
    private static boolean isUnchanged(String key, Object value, Map<String, Object> current) {
        var stored = current.get(key);
        return stored == null ? Boolean.FALSE.equals(value) || "".equals(value) : Objects.equals(value, stored);
    }

    /**
     * Writes {@code banner-enabled} last when switching on and first when switching off: each key
     * is its own transaction, and a reader must never see the new switch state beside the old
     * message.
     */
    private void write(Map<String, Object> updates) {
        var enabled = updates.get(SETTING_BANNER_ENABLED);
        if (Boolean.FALSE.equals(enabled)) {
            cache.set(SETTING_BANNER_ENABLED, false);
        }
        updates.forEach((key, value) -> {
            if (!SETTING_BANNER_ENABLED.equals(key)) {
                cache.set(key, value);
            }
        });
        if (Boolean.TRUE.equals(enabled)) {
            cache.set(SETTING_BANNER_ENABLED, true);
        }
    }

    /** Key names only for the message: the admin log message column caps at 512 characters. */
    private String describe(Map<String, Object> updates) {
        return updates.entrySet()
                .stream()
                .map(
                        entry -> SETTING_BANNER_MESSAGE.equals(entry.getKey())
                                ? entry.getKey()
                                : entry.getKey() + " -> " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Keeps the stored dismiss token unless the caller sends a new one, so correcting the message
     * doesn't bring the banner back for everyone who dismissed it. A banner appearing where there
     * was none is a new one to dismiss, whatever token the caller echoed back.
     */
    private String dismissIdToStore(Map<String, Object> updates, Map<String, Object> current) {
        var currentMessage = string(current, SETTING_BANNER_MESSAGE);
        var newMessage = updates.containsKey(SETTING_BANNER_MESSAGE)
                ? string(updates, SETTING_BANNER_MESSAGE)
                : currentMessage;
        if (currentMessage.isBlank() && !newMessage.isBlank()) {
            return UUID.randomUUID().toString();
        }

        var supplied = string(updates, SETTING_BANNER_DISMISS_ID);
        return supplied.isBlank() ? string(current, SETTING_BANNER_DISMISS_ID) : supplied;
    }

    private static String string(Map<String, Object> settings, String key) {
        return settings.get(key) instanceof String value ? value : "";
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
