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

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether the web UI shows download analytics on extension pages; the series endpoint serves data
 * regardless. Off by default, so a registry can backfill its download history before the extension
 * pages show a graph. Only meaningful where {@code ovsx.analytics.enabled} wires download analytics
 * at all.
 */
@Component
public class DownloadAnalyticsSetting implements WritableSetting<Boolean>, PublicSetting {

    public static final String KEY = "download-analytics-visible";

    private final boolean analyticsConfigured;

    public DownloadAnalyticsSetting(@Value("${ovsx.analytics.enabled:false}") boolean analyticsConfigured) {
        this.analyticsConfigured = analyticsConfigured;
    }

    /** Whether {@code ovsx.analytics.enabled} wires download analytics at all; the switch only applies then. */
    public boolean isConfigured() {
        return analyticsConfigured;
    }

    @Override
    public String getName() {
        return KEY;
    }

    @Override
    public Boolean read(SettingRows stored) {
        return stored.asBoolean(KEY, false);
    }

    @Override
    public Boolean merge(Boolean current, SettingRows update) {
        return update.asBoolean(KEY, current);
    }

    @Override
    public Map<String, Object> toRows(Boolean value) {
        return Map.of(KEY, value);
    }

    /** Switching on where nothing serves the analytics deserves a message, not a silent no-op. */
    @Override
    public void validate(Boolean value, Boolean current) {
        if (value && !current && !analyticsConfigured) {
            throw WritableSetting
                    .reject("Download analytics is not enabled on this registry, see ovsx.analytics.enabled.");
        }
    }

    /** Always published, as the effective state: a switch left on after analytics was unconfigured reads as off. */
    @Override
    public Map<String, Object> publicView(SettingRows stored) {
        return Map.of(KEY, effective(stored));
    }

    private boolean effective(SettingRows stored) {
        return analyticsConfigured && read(stored);
    }
}
