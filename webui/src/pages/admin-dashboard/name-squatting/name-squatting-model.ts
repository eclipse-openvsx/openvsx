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

import type {
    NameSquattingAllow,
    NameSquattingAllowlistGroup,
    NameSquattingExtension,
    NameSquattingField,
    NameSquattingGroup
} from '../../../extension-registry-types';
import { extensionId } from './use-name-squatting';

export const FIELD_LABELS: Record<NameSquattingAllow['field'], string> = {
    NAME: 'Extension name',
    DISPLAY_NAME: 'Display name',
    NAMESPACE: 'Namespace'
};

/**
 * Namespace similarity only corroborates a name match, so it is mentioned from here up and is
 * otherwise noise: unrelated namespaces still share a trigram or two.
 */
export const NAMESPACE_HINT_THRESHOLD = 0.5;

/** Trigram similarity can reach 1 for names that are not equal (punctuation is ignored), so 100% is not "Exact". */
export const formatSimilarity = (similarity: number, exact: boolean): string =>
    exact ? 'Exact' : `${Math.round(similarity * 100)}%`;

export const similarityColor = (similarity: number, exact: boolean): 'error' | 'warning' | 'default' => {
    if (exact) {
        return 'error';
    }
    return similarity >= 0.85 ? 'warning' : 'default';
};

export const pluralize = (count: number, noun: string): string => `${count} ${noun}${count === 1 ? '' : 's'}`;

export interface CollisionFilter {
    fields: ReadonlySet<NameSquattingField>;
    exactOnly: boolean;
    query: string;
}

const matchesQuery = (query: string, extensions: NameSquattingExtension[]): boolean => {
    const needle = query.trim().toLowerCase();
    return (
        needle.length === 0 ||
        extensions.some(extension =>
            `${extensionId(extension)} ${extension.displayName ?? ''}`.toLowerCase().includes(needle)
        )
    );
};

/**
 * Narrows the loaded groups to the matches on the chosen fields. A look-alike left with no match is
 * dropped, and so is a group left with no look-alike; the query keeps a group when its anchor or any
 * remaining look-alike contains it.
 */
export function filterCollisionGroups(groups: NameSquattingGroup[], filter: CollisionFilter): NameSquattingGroup[] {
    return groups.flatMap(group => {
        const lookalikes = group.lookalikes.flatMap(lookalike => {
            const matches = lookalike.matches.filter(
                match => filter.fields.has(match.field) && (!filter.exactOnly || match.exact)
            );
            return matches.length > 0 ? [{ ...lookalike, matches }] : [];
        });
        if (
            lookalikes.length === 0 ||
            !matchesQuery(filter.query, [group.anchor, ...lookalikes.map(lookalike => lookalike.extension)])
        ) {
            return [];
        }
        return [{ anchor: group.anchor, lookalikes }];
    });
}

export interface AllowlistFilter {
    renamedOnly: boolean;
    query: string;
}

export function filterAllowlistGroups(
    groups: NameSquattingAllowlistGroup[],
    filter: AllowlistFilter
): NameSquattingAllowlistGroup[] {
    return groups.filter(
        group =>
            (!filter.renamedOnly || group.allows.some(allow => allow.renamed)) &&
            matchesQuery(filter.query, [group.extension, ...group.allows.map(allow => allow.other)])
    );
}
