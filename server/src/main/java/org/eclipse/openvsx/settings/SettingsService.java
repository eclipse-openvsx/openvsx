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

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.json.SiteSettingsJson;
import org.eclipse.openvsx.util.AfterCommitExecutor;

/**
 * Serves the registry's settings to the two endpoints that read them, as their JSON documents, and
 * applies an admin's update across every registered {@link WritableSetting}. It knows what a setting is, never what any
 * particular setting means: names, row keys, legal values, and defaults all belong to
 * the bean. Code that needs a setting's value at runtime injects that setting and asks it, see the
 * package documentation, not this.
 */
@Service
public class SettingsService {

    private final List<WritableSetting<?>> settings;
    private final SettingsJsonMapper mapper;
    private final SettingsStore store;
    private final SettingsUpdateChannel channel;
    private final AfterCommitExecutor afterCommit;
    private final ApplicationEventPublisher events;

    /**
     * Settings are ordered by name so the audit line and the public settings object do not reshuffle
     * with bean discovery order.
     */
    SettingsService(
            List<WritableSetting<?>> settings,
            SettingsJsonMapper mapper,
            SettingsStore store,
            SettingsUpdateChannel channel,
            AfterCommitExecutor afterCommit,
            ApplicationEventPublisher events
    ) {
        this.settings = settings.stream().sorted(Comparator.comparing(WritableSetting::getName)).toList();
        this.mapper = mapper;
        this.store = store;
        this.channel = channel;
        this.afterCommit = afterCommit;
        this.events = events;
    }

    /** Every setting with its defaults applied, drafts included. The admin view. */
    public SettingsJson getCurrentSettings() {
        return mapper.toJson(currentRows());
    }

    /** Only what the settings implementing {@link PublicSetting} choose to publish. */
    public SiteSettingsJson getSiteSettings() {
        return mapper.toSiteJson(siteRows());
    }

    /** Applies an admin's update, where an absent field leaves its setting unchanged, and returns the audit line. */
    public String update(SettingsJson update) {
        return updateRows(mapper.toRows(update));
    }

    SettingRows currentRows() {
        var stored = store.snapshot();
        var rows = new LinkedHashMap<String, Object>();
        settings.forEach(setting -> rows.putAll(currentRowsOf(setting, stored)));
        return new SettingRows(rows);
    }

    SettingRows siteRows() {
        var stored = store.snapshot();
        var rows = new LinkedHashMap<String, Object>();
        for (var setting : settings) {
            if (setting instanceof PublicSetting published) {
                rows.putAll(published.publicView(stored));
            }
        }
        return new SettingRows(rows);
    }

    /**
     * Drops every cached setting on this node and tells the others to. For callers that change data
     * a setting derives from, such as the size overrides behind the ceiling: evicting one key is not
     * enough. Deferred to after the commit, or a node could refill the cache from the rows as they
     * still are.
     */
    public void invalidateCache() {
        afterCommit.execute(() -> {
            store.clear();
            channel.publish();
            events.publishEvent(new SettingsChangedEvent());
        });
    }

    /**
     * Every setting is merged and validated before anything is written, so a refusal leaves the store
     * untouched; the rows then go in as one transaction.
     */
    String updateRows(SettingRows update) {
        var stored = store.load();

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

        store.setAll(pending);
        store.clear();
        channel.publish();
        afterCommit.execute(() -> events.publishEvent(new SettingsChangedEvent()));
        return String.join(", ", described);
    }

    /** Merges, validates and returns the rows this setting wants written. */
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
        // A row the update carries is written even when it equals the current value: that value
        // comes from a node-local snapshot that can be a minute stale, so skipping the write could
        // drop the very change the admin asked for.
        changed.entrySet()
                .removeIf(
                        row -> !update.rows().containsKey(row.getKey())
                                && Objects.equals(row.getValue(), currentRows.get(row.getKey())));
        return changed;
    }

    private static <T> Map<String, Object> currentRowsOf(WritableSetting<T> setting, SettingRows stored) {
        return setting.toRows(setting.read(stored));
    }
}
