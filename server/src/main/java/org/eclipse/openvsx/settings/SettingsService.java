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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.util.AfterCommitExecutor;

/**
 * Serves the registry's settings to the two endpoints that read them and applies an admin's update
 * across every registered {@link WritableSetting}. It knows what a setting is, never what any
 * particular setting means: names, row keys, legal values, defaults and write order all belong to
 * the bean.
 */
@Service
public class SettingsService {

    private final List<WritableSetting<?>> settings;
    private final ReadOnlySetting readOnly;
    private final MaxExtensionSizeSetting maxExtensionSize;
    private final SettingsCache cache;
    private final SettingsUpdateChannel channel;
    private final AfterCommitExecutor afterCommit;

    /**
     * {@code readOnly} is injected only to serve the deprecated {@link #isReadOnly()}, and leaves
     * with it. Settings are ordered by name so the audit line and the public settings object do not
     * reshuffle with bean discovery order.
     */
    public SettingsService(
            List<WritableSetting<?>> settings,
            ReadOnlySetting readOnly,
            MaxExtensionSizeSetting maxExtensionSize,
            SettingsCache cache,
            SettingsUpdateChannel channel,
            AfterCommitExecutor afterCommit
    ) {
        this.settings = settings.stream().sorted(Comparator.comparing(WritableSetting::getName)).toList();
        this.readOnly = readOnly;
        this.maxExtensionSize = maxExtensionSize;
        this.cache = cache;
        this.channel = channel;
        this.afterCommit = afterCommit;
    }

    /** @deprecated inject {@link ReadOnlySetting} and call {@link ReadOnlySetting#isEnabled()}. */
    @Deprecated
    public boolean isReadOnly() {
        return readOnly.isEnabled();
    }

    public long getMaxExtensionSize() {
        return maxExtensionSize.getValue();
    }

    /** Every setting's rows with its defaults applied, drafts included. The admin view. */
    public SettingsJson getCurrentSettings() {
        var stored = cache.snapshot();
        var rows = new LinkedHashMap<String, Object>();
        settings.forEach(setting -> rows.putAll(currentRows(setting, stored)));
        var json = SettingsJson.of(rows);
        json.setMaxOverrideSize(maxExtensionSize.getCeiling());
        return json;
    }

    /** Only what the settings implementing {@link PublicSetting} choose to publish. */
    public Map<String, Object> getSiteSettings() {
        var stored = cache.snapshot();
        var rows = new LinkedHashMap<String, Object>();
        for (var setting : settings) {
            if (setting instanceof PublicSetting published) {
                rows.putAll(published.publicView(stored));
            }
        }
        return rows;
    }

    /**
     * Drops every cached setting on this node and tells the others to. For callers that change data
     * a setting derives from, such as the size overrides behind the ceiling: evicting one key is not
     * enough. Deferred to after the commit, or a node could refill the cache from the rows as they
     * still are.
     */
    public void invalidateCache() {
        afterCommit.execute(() -> {
            cache.clear();
            channel.publish();
        });
    }

    /**
     * Applies an admin's update and returns the audit line. Every setting is merged and validated
     * before the first row is written: each row is its own transaction, so a refusal found halfway
     * through would leave a save half applied.
     */
    public String updateFromJson(SettingsJson newSettings) {
        var update = new SettingRows(newSettings.toRows());
        var stored = cache.snapshot();

        var pending = new LinkedHashMap<String, Object>();
        var described = new ArrayList<String>();
        for (var setting : settings) {
            changedRows(setting, stored, update).forEach((rowKey, value) -> {
                pending.put(rowKey, value);
                described.add(setting.describe(rowKey, value));
            });
        }
        if (pending.isEmpty()) {
            return "";
        }

        pending.forEach(cache::set);
        channel.publish();
        return String.join(", ", described);
    }

    /** Merges, validates and returns the rows this setting wants written, in its own write order. */
    private static <T> Map<String, Object> changedRows(
            WritableSetting<T> setting,
            SettingRows stored,
            SettingRows update
    ) {
        var current = setting.read(stored);
        var merged = setting.merge(current, update);
        setting.validate(merged, current);

        var currentRows = setting.toRows(current);
        var changed = new LinkedHashMap<String, Object>(setting.toRows(merged));
        changed.entrySet()
                .removeIf(
                        row -> Objects.equals(row.getValue(), currentRows.get(row.getKey()))
                                && !(setting.writesUnchanged() && update.rows().containsKey(row.getKey())));
        return changed;
    }

    private static <T> Map<String, Object> currentRows(WritableSetting<T> setting, SettingRows stored) {
        return setting.toRows(setting.read(stored));
    }
}
