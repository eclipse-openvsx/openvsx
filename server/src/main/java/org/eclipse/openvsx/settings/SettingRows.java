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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rows keyed the way the store keys them - either one read of the whole store or one admin's
 * update. Accessors fall back rather than throw: the values come from the database, and a setting
 * that refused to load a row someone had written by hand would take the registry down with it.
 */
public record SettingRows(Map<String, Object> rows) {

    /** Not {@code Map.copyOf}: that rejects a null value. */
    public SettingRows {
        rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
    }

    public boolean asBoolean(String rowKey, boolean fallback) {
        return rows.get(rowKey) instanceof Boolean value ? value : fallback;
    }

    public long asLong(String rowKey, long fallback) {
        return rows.get(rowKey) instanceof Number value ? value.longValue() : fallback;
    }

    public String asString(String rowKey, String fallback) {
        return rows.get(rowKey) instanceof String value ? value : fallback;
    }

    /** The rows out of {@code rowKeys} that are actually stored, absent ones left out rather than defaulted. */
    public Map<String, Object> only(Collection<String> rowKeys) {
        var selected = new LinkedHashMap<String, Object>();
        for (var rowKey : rowKeys) {
            var value = rows.get(rowKey);
            if (value != null) {
                selected.put(rowKey, value);
            }
        }
        return selected;
    }
}
