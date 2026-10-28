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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.json.SiteSettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;

/**
 * The one place that turns stored rows into the wire DTOs and back, and so the one class that spells
 * every setting's row keys; the DTOs themselves name no setting. Properties are camelCase like every
 * other DTO, rows kebab-case as stored. A row of a setting added outside this package travels as an
 * additional property named after its key, {@code audit-log-enabled} as {@code auditLogEnabled}.
 */
@Component
class SettingsJsonMapper {

    private static final Set<String> BUILT_IN_KEYS = Set.of(
            ReadOnlySetting.KEY,
            MaxExtensionSizeSetting.KEY,
            BannerSetting.KEY_ENABLED,
            BannerSetting.KEY_MESSAGE,
            BannerSetting.KEY_SEVERITY,
            BannerSetting.KEY_DISMISS_ID,
            DownloadAnalyticsSetting.KEY);
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private final PublishingConfig publishingConfig;
    private final DownloadAnalyticsSetting downloadAnalytics;

    SettingsJsonMapper(PublishingConfig publishingConfig, DownloadAnalyticsSetting downloadAnalytics) {
        this.publishingConfig = publishingConfig;
        this.downloadAnalytics = downloadAnalytics;
    }

    /**
     * The admin view: every row the settings report, plus two pieces of configuration rather than
     * settings, the ceiling no size can be raised past and whether download analytics is configured
     * at all. A field no row backs stays null, and so off the wire.
     */
    SettingsJson toJson(SettingRows stored) {
        var rows = stored.rows();
        var json = new SettingsJson();
        json.setReadOnly(bool(rows, ReadOnlySetting.KEY));
        json.setMaxExtensionSize(number(rows, MaxExtensionSizeSetting.KEY));
        json.setMaxOverrideSize(publishingConfig.getMaxOverrideSize());
        json.setBannerEnabled(bool(rows, BannerSetting.KEY_ENABLED));
        json.setBannerMessage(string(rows, BannerSetting.KEY_MESSAGE));
        json.setBannerSeverity(string(rows, BannerSetting.KEY_SEVERITY));
        json.setBannerDismissId(string(rows, BannerSetting.KEY_DISMISS_ID));
        json.setDownloadAnalyticsVisible(
                downloadAnalytics.isConfigured() ? bool(rows, DownloadAnalyticsSetting.KEY) : null);
        additionalRows(rows, BUILT_IN_KEYS).forEach(json::setAdditionalSetting);
        return json;
    }

    /** The rows an update carries. A null field is a setting the caller isn't touching, so it produces no row. */
    SettingRows toRows(SettingsJson json) {
        var rows = new LinkedHashMap<String, Object>();
        putIfPresent(rows, ReadOnlySetting.KEY, json.getReadOnly());
        putIfPresent(rows, MaxExtensionSizeSetting.KEY, json.getMaxExtensionSize());
        putIfPresent(rows, BannerSetting.KEY_ENABLED, json.getBannerEnabled());
        putIfPresent(rows, BannerSetting.KEY_MESSAGE, json.getBannerMessage());
        putIfPresent(rows, BannerSetting.KEY_SEVERITY, json.getBannerSeverity());
        putIfPresent(rows, BannerSetting.KEY_DISMISS_ID, json.getBannerDismissId());
        putIfPresent(rows, DownloadAnalyticsSetting.KEY, json.getDownloadAnalyticsVisible());
        // A built-in setting is written only through its own property, never under another spelling.
        json.getAdditionalSettings().forEach((name, value) -> {
            var key = toRowKey(name);
            if (value != null && PROPERTY_NAME.matcher(name).matches() && !BUILT_IN_KEYS.contains(key)) {
                rows.put(key, value);
            }
        });
        return new SettingRows(rows);
    }

    /** The published rows; a row with no property on the site DTO is left out. */
    SiteSettingsJson toSiteJson(SettingRows published) {
        var rows = published.rows();
        var json = new SiteSettingsJson();
        json.setBannerEnabled(bool(rows, BannerSetting.KEY_ENABLED));
        json.setBannerMessage(string(rows, BannerSetting.KEY_MESSAGE));
        json.setBannerSeverity(string(rows, BannerSetting.KEY_SEVERITY));
        json.setBannerDismissId(string(rows, BannerSetting.KEY_DISMISS_ID));
        json.setDownloadAnalyticsVisible(bool(rows, DownloadAnalyticsSetting.KEY));
        // Built-in rows only ever reach visitors through their own properties above.
        additionalRows(rows, BUILT_IN_KEYS).forEach(json::setAdditionalSetting);
        return json;
    }

    /** The rows no built-in property carries, under their property names. */
    private static Map<String, Object> additionalRows(Map<String, Object> rows, Set<String> builtIn) {
        var additional = new LinkedHashMap<String, Object>();
        rows.forEach((key, value) -> {
            if (!builtIn.contains(key)) {
                additional.put(toPropertyName(key), value);
            }
        });
        return additional;
    }

    private static String toPropertyName(String rowKey) {
        var name = new StringBuilder();
        var upper = false;
        for (var c : rowKey.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return name.toString();
    }

    private static String toRowKey(String propertyName) {
        return propertyName.replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ROOT);
    }

    private static void putIfPresent(Map<String, Object> rows, String key, @Nullable Object value) {
        if (value != null) {
            rows.put(key, value);
        }
    }

    private static @Nullable Boolean bool(Map<String, Object> rows, String key) {
        return rows.get(key) instanceof Boolean value ? value : null;
    }

    private static @Nullable Long number(Map<String, Object> rows, String key) {
        return rows.get(key) instanceof Number value ? value.longValue() : null;
    }

    private static @Nullable String string(Map<String, Object> rows, String key) {
        return rows.get(key) instanceof String value ? value : null;
    }
}
