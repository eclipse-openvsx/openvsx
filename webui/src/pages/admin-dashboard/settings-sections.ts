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

import type { Settings } from '../../extension-registry-types';

/** A runtime setting shown as an on/off switch on the admin dashboard's Settings page. */
export interface AdminSettingsFlag {
    /**
     * The `Settings` property the switch reads and writes. For a contributed switch, the camelCase
     * name of a boolean setting the deployment's server registers: a setting stored under
     * `audit-log-enabled` is read and written as `auditLogEnabled` on `/admin/settings`.
     */
    key: string;
    title: string;
    description: string;
}

/**
 * A titled group of switches on the Settings page, contributed through
 * `PageSettings.elements.adminSettingsSections`. Additive only: a built-in switch cannot be
 * replaced, so a switch whose key is already in use is ignored.
 */
export interface AdminSettingsSection {
    /**
     * A built-in id - `registry`, `site`, `publishing` or `analytics` - appends the switches to that
     * section, where they share its availability: `analytics` is left out where the server has no
     * download analytics configured. Any other id adds a section of its own; sections
     * sharing an id are merged into one.
     */
    id: string;
    /** Ignored for a built-in id. A section of your own is titled by its id without one. */
    title?: string;
    description?: string;
    flags: AdminSettingsFlag[];
    /** Outlines the section while it holds unsaved edits, for settings worth a second look. Ignored for a built-in id. */
    warnOnChange?: boolean;
    /**
     * Leaves the whole section out when it returns false for the loaded settings, for switches the
     * server would refuse, such as one over a feature it has not configured. Ignored for a built-in id.
     */
    available?: (settings: Settings) => boolean;
}

/** A section as the page renders it, with the contributed switches folded into the built-in ones. */
export interface ResolvedSettingsSection {
    id: string;
    title: string;
    description: string;
    flags: AdminSettingsFlag[];
    warnOnChange: boolean;
    available?: (settings: Settings) => boolean;
}

const flag = (key: keyof Settings, title: string, description: string): AdminSettingsFlag => ({
    key,
    title,
    description
});

/** The sections the page always shows. The banner and the size limit are rendered by the page itself. */
export const BUILT_IN_SECTIONS: readonly ResolvedSettingsSection[] = [
    {
        id: 'registry',
        title: 'Registry',
        description: 'How the registry service behaves for every caller.',
        flags: [
            flag(
                'readOnly',
                'Read-only mode',
                'Blocks write operations while keeping browsing, search, and downloads available.'
            )
        ],
        warnOnChange: true
    },
    // No change outline here: unlike read-only mode, a banner edit is not worth a warning.
    {
        id: 'site',
        title: 'Site settings',
        description: 'What visitors see on the web UI.',
        flags: [],
        warnOnChange: false
    },
    {
        id: 'publishing',
        title: 'Publishing',
        description: 'Limits applied when extensions are published.',
        flags: [],
        warnOnChange: false
    },
    // Only where the server has analytics configured: the switch would be refused otherwise.
    {
        id: 'analytics',
        title: 'Analytics',
        description: 'What the registry reports about how extensions are used.',
        flags: [
            flag(
                'downloadAnalyticsVisible',
                'Weekly downloads on the extension page',
                'Shows the weekly downloads card on the extension details page.'
            )
        ],
        warnOnChange: false,
        available: settings => settings.downloadAnalyticsVisible !== undefined
    }
];

/** The settings the page renders itself, so a contributed switch cannot double up on them either. */
const PAGE_KEYS: readonly (keyof Settings)[] = [
    'maxExtensionSize',
    'maxOverrideSize',
    'bannerEnabled',
    'bannerMessage',
    'bannerSeverity',
    'bannerDismissId'
];

/** The built-in sections with the contributed ones folded in, sorted by title as the page shows them. */
export const withContributedSections = (
    contributed: readonly AdminSettingsSection[] = []
): ResolvedSettingsSection[] => {
    const sections = BUILT_IN_SECTIONS.map(section => ({ ...section, flags: [...section.flags] }));
    const used = new Set<string>([...PAGE_KEYS, ...sections.flatMap(section => section.flags.map(({ key }) => key))]);
    for (const extra of contributed) {
        // Recorded as each switch is accepted, so a key repeated within one contribution is caught too.
        const flags: AdminSettingsFlag[] = [];
        for (const candidate of extra.flags) {
            if (!used.has(candidate.key)) {
                used.add(candidate.key);
                flags.push(candidate);
            }
        }
        const target = sections.find(section => section.id === extra.id);
        if (target) {
            target.flags.push(...flags);
        } else if (flags.length > 0) {
            sections.push({
                id: extra.id,
                title: extra.title ?? extra.id,
                description: extra.description ?? '',
                flags,
                warnOnChange: extra.warnOnChange ?? false,
                available: extra.available
            });
        }
    }
    return sections.sort((a, b) => a.title.localeCompare(b.title));
};
