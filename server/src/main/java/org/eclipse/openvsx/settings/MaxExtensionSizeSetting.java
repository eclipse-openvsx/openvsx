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

import org.springframework.stereotype.Component;

import org.eclipse.openvsx.publish.PublishingConfig;

/**
 * The default size limit for a published package, applied when no namespace or extension override
 * does. Falls back to {@code ovsx.publishing.max-content-size} until an admin stores a value.
 */
@Component
public class MaxExtensionSizeSetting implements WritableSetting<Long> {

    public static final String KEY = "max-extension-size";

    private final SettingsCache cache;
    private final PublishingConfig publishingConfig;

    public MaxExtensionSizeSetting(SettingsCache cache, PublishingConfig publishingConfig) {
        this.cache = cache;
        this.publishingConfig = publishingConfig;
    }

    public long getValue() {
        return read(cache.snapshot());
    }

    @Override
    public String getName() {
        return KEY;
    }

    @Override
    public Long read(SettingRows stored) {
        return stored.asLong(KEY, publishingConfig.getMaxContentSize());
    }

    @Override
    public Long merge(Long current, SettingRows update) {
        return update.asLong(KEY, current);
    }

    @Override
    public Map<String, Object> toRows(Long value) {
        return Map.of(KEY, value);
    }

    @Override
    public void validate(Long value, Long current) {
        if (value <= 0) {
            throw WritableSetting.reject("Max extension size must be greater than zero.");
        }
        var ceiling = publishingConfig.getMaxOverrideSize();
        if (value > ceiling) {
            throw WritableSetting.reject("Max extension size exceeds the maximum of " + ceiling + " bytes.");
        }
    }
}
