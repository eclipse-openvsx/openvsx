/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { useContext, useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { SearchEntry, SortOrder } from '../../extension-registry-types';
import { ExtensionCategory } from '../../extension-registry-types';
import { useCategories } from '../../components/categories';
import { HomeCuratedSection } from '../../page-settings';

/** Number of extensions fetched for each curated row. */
export const CURATED_SIZE = 7;

/** Categories shown in the home page grid. */
const HOME_CATEGORIES = new Set<ExtensionCategory>([
    'AI',
    'Programming Languages',
    'Snippets',
    'Linters',
    'Themes',
    'Debuggers',
    'Formatters',
    'Keymaps',
    'SCM Providers',
    'Extension Packs',
    'Language Packs',
    'Data Science',
    'Machine Learning',
    'Visualization',
    'Notebooks'
]);

/** Curated rows shown when the consumer does not configure `home.curatedSections`. */
export const DEFAULT_CURATED_SECTIONS: HomeCuratedSection[] = [
    { title: 'Featured', subtitle: 'Top picks ranked by relevance', sortBy: 'relevance' },
    { title: 'Most downloaded', subtitle: 'The extensions developers rely on every day', sortBy: 'downloadCount' },
    { title: 'Recently updated', subtitle: 'Fresh releases from publishers this week', sortBy: 'timestamp' }
];

export interface CuratedRow extends HomeCuratedSection {
    extensions: SearchEntry[];
    loading: boolean;
    error: Error | null;
    /** Loads the row again, e.g. from a retry button after it failed. */
    refetch: () => void;
}

/** The browsable home category list: the registry's categories minus a few noisy ones. */
export function useHomeCategories(): ExtensionCategory[] {
    const allCategories = useCategories();
    return useMemo(() => allCategories.filter(c => HOME_CATEGORIES.has(c)), [allCategories]);
}

/**
 * Loads the curated extension rows, each fetched with its configured ordering.
 * Rows start in a loading state and fill in as requests resolve; a failed row
 * carries its `error` and loads again on `refetch`.
 */
export function useCuratedRows(curatedSections: HomeCuratedSection[]): CuratedRow[] {
    const { service } = useContext(MainContext);

    const results = useQueries({
        queries: curatedSections.map(section => ({
            queryKey: ['home-curated', section.sortBy],
            queryFn: async ({ signal }: { signal: AbortSignal }): Promise<SearchEntry[]> => {
                const result = await service.search(signal, {
                    query: '',
                    category: '',
                    offset: 0,
                    size: CURATED_SIZE,
                    sortBy: section.sortBy,
                    sortOrder: 'desc' as SortOrder
                });
                return result.extensions;
            }
        }))
    });

    return curatedSections.map((section, idx) => {
        const { data, error, isFetching, refetch } = results[idx];
        return {
            ...section,
            extensions: data ?? [],
            // A retry after a failure isn't `isLoading` (the query stays in error), hence `!data`.
            loading: isFetching && !data,
            error,
            refetch: () => void refetch()
        };
    });
}
