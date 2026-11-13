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

import { describe, expect, it } from 'vitest';
import type {
    NameSquattingAllowlistGroup,
    NameSquattingExtension,
    NameSquattingGroup
} from '../../../../../src/extension-registry-types';
import {
    filterAllowlistGroups,
    filterCollisionGroups,
    formatSimilarity
} from '../../../../../src/pages/admin-dashboard/name-squatting/name-squatting-model';
import {
    mergeAllowlistPages,
    mergeCollisionPages
} from '../../../../../src/pages/admin-dashboard/name-squatting/use-name-squatting';

const ext = (namespace: string, name: string, displayName = name): NameSquattingExtension => ({
    namespace,
    name,
    displayName,
    downloadCount: 0,
    activeVersionCount: 1,
    active: true
});

const group: NameSquattingGroup = {
    anchor: ext('ms-python', 'python', 'Python'),
    lookalikes: [
        {
            extension: ext('pyth0n', 'python', 'Python'),
            matches: [
                { field: 'DISPLAY_NAME', value: 'Python', lookalikeValue: 'Python', similarity: 1, exact: true },
                { field: 'NAME', value: 'python', lookalikeValue: 'python', similarity: 1, exact: true }
            ],
            namespaceSimilarity: 0.2
        },
        {
            extension: ext('labs', 'pythom', 'Pythom'),
            matches: [{ field: 'NAME', value: 'python', lookalikeValue: 'pythom', similarity: 0.56, exact: false }],
            namespaceSimilarity: 0
        }
    ]
};

const bothFields = new Set(['NAME', 'DISPLAY_NAME'] as const);

describe('filterCollisionGroups', () => {
    it('keeps only the matches on the chosen fields and drops look-alikes left with none', () => {
        const [filtered] = filterCollisionGroups([group], {
            fields: new Set(['DISPLAY_NAME'] as const),
            exactOnly: false,
            query: ''
        });

        expect(filtered.lookalikes.map(lookalike => lookalike.extension.namespace)).toEqual(['pyth0n']);
        expect(filtered.lookalikes[0].matches.map(match => match.field)).toEqual(['DISPLAY_NAME']);
    });

    it('drops a group none of whose look-alikes is an exact match when only exact ones are asked for', () => {
        const fuzzyOnly = { ...group, lookalikes: [group.lookalikes[1]] };

        expect(filterCollisionGroups([fuzzyOnly], { fields: bothFields, exactOnly: true, query: '' })).toEqual([]);
    });

    it('keeps a whole group when the query names its anchor', () => {
        const [filtered] = filterCollisionGroups([group], { fields: bothFields, exactOnly: false, query: 'MS-PY' });

        expect(filtered.lookalikes).toHaveLength(2);
    });

    it('matches the query against display names too', () => {
        expect(filterCollisionGroups([group], { fields: bothFields, exactOnly: false, query: 'pythom' })).toHaveLength(
            1
        );
        expect(filterCollisionGroups([group], { fields: bothFields, exactOnly: false, query: 'rust' })).toEqual([]);
    });
});

describe('filterAllowlistGroups', () => {
    const allowGroup = (renamed: boolean): NameSquattingAllowlistGroup => ({
        extension: ext(renamed ? 'renamed' : 'kept', 'x'),
        allows: [
            {
                id: renamed ? 1 : 2,
                other: ext('a', 'x'),
                field: 'NAME',
                similarity: 1,
                value: 'x',
                otherValue: 'x',
                renamed,
                decidedAt: '2026-11-01T00:00:00Z'
            }
        ]
    });

    it('keeps only the groups with a pair renamed since it was allowed', () => {
        const filtered = filterAllowlistGroups([allowGroup(false), allowGroup(true)], {
            renamedOnly: true,
            query: ''
        });

        expect(filtered.map(g => g.extension.namespace)).toEqual(['renamed']);
    });
});

describe('formatSimilarity', () => {
    // Trigram similarity ignores punctuation, so it can reach 1 for names that are not equal.
    it('calls only an exact match exact', () => {
        expect(formatSimilarity(1, true)).toBe('Exact');
        expect(formatSimilarity(1, false)).toBe('100%');
        expect(formatSimilarity(0.564, false)).toBe('56%');
    });
});

describe('merging pages', () => {
    // Batches are cut by candidate, so one anchor can head a group in two of them.
    it('shows an anchor resembled in two batches as one group, each look-alike once', () => {
        const [first, second] = group.lookalikes;
        const merged = mergeCollisionPages({
            pageParams: [0, 25],
            pages: [
                { groups: [{ anchor: group.anchor, lookalikes: [first] }], nextOffset: 25, hasMore: true },
                { groups: [{ anchor: group.anchor, lookalikes: [first, second] }], nextOffset: 50, hasMore: false }
            ]
        });

        expect(merged).toHaveLength(1);
        expect(merged[0].lookalikes.map(lookalike => lookalike.extension.namespace)).toEqual(['pyth0n', 'labs']);
    });

    it('shows an allowed extension split across two pages once', () => {
        const allow = (id: number) => ({
            id,
            other: ext('a', `x${id}`),
            field: 'NAME' as const,
            similarity: 0.8,
            value: 'x',
            otherValue: 'x',
            renamed: false,
            decidedAt: '2026-11-01T00:00:00Z'
        });
        const extension = ext('b', 'x');
        const merged = mergeAllowlistPages({
            pageParams: [0, 1],
            pages: [
                { groups: [{ extension, allows: [allow(1)] }], page: 0, hasMore: true },
                { groups: [{ extension, allows: [allow(1), allow(2)] }], page: 1, hasMore: false }
            ]
        });

        expect(merged).toHaveLength(1);
        expect(merged[0].allows.map(a => a.id)).toEqual([1, 2]);
    });
});
