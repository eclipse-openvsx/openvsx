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

/**
 * Blocks write operations while keeping browsing, search and downloads available. Not a
 * {@link PublicSetting}: the web UI learns that the registry is read-only from the 409s
 * {@link ReadOnlyEndpointAspect} returns, not from a published flag.
 */
@Component
public class ReadOnlySetting implements WritableSetting<Boolean> {

    /** Shipped before settings had owners, so the name is also its bare row key. */
    public static final String KEY = "read-only";

    private final SettingsCache cache;

    public ReadOnlySetting(SettingsCache cache) {
        this.cache = cache;
    }

    /**
     * The only setting read from inside the server rather than through an endpoint, and so the only
     * one that reads the store itself.
     */
    public boolean isEnabled() {
        return read(cache.snapshot());
    }

    @Override
    public String getName() {
        return KEY;
    }

    /** An empty store means writable: the registry fails open. */
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
}
