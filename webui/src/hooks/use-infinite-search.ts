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

import { useContext } from 'react';
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { MainContext } from '../context';
import { SearchResult } from '../extension-registry-types';
import { ExtensionFilter } from '../extension-registry-service';

/** The filter fields that identify a result set; `offset` is driven by the pages. */
export type SearchQuery = Omit<ExtensionFilter, 'offset'>;

export const searchKeys = {
    list: (query: SearchQuery) => ['search', query] as const
};

/**
 * Loads search results one offset-paged page at a time. The pages stay in the
 * query cache keyed by the filter, so returning to a scrolled search restores
 * every loaded page (and thus its scroll position) instead of refetching from
 * the first page. `keepPreviousData` keeps the current results on screen while
 * a changed filter loads.
 */
export const useInfiniteSearch = (filter: ExtensionFilter) => {
    const { service } = useContext(MainContext);
    const { query, category, size, sortBy, sortOrder } = filter;
    return useInfiniteQuery({
        queryKey: searchKeys.list({ query, category, size, sortBy, sortOrder }),
        queryFn: ({ pageParam, signal }): Promise<SearchResult> =>
            service.search(signal, {
                query,
                category,
                size,
                sortBy,
                sortOrder,
                offset: pageParam
            }),
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) => {
            const loaded = allPages.reduce((sum, page) => sum + page.extensions.length, 0);
            return lastPage.extensions.length > 0 && loaded < lastPage.totalSize ? loaded : undefined;
        },
        placeholderData: keepPreviousData
    });
};
