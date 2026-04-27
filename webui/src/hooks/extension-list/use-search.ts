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
import { useInfiniteQuery } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { ExtensionFilter } from '../../extension-registry-service';
import { SearchResult } from '../../extension-registry-types';

export const SEARCH_QUERY_KEY = 'extension-search';

const buildSearchUrl = (serverUrl: string, filter: ExtensionFilter & { offset: number }): URL => {
    const url = new URL('/api/-/search', serverUrl);
    if (filter.query)     url.searchParams.set('query',     filter.query);
    if (filter.category)  url.searchParams.set('category',  filter.category);
    if (filter.offset)    url.searchParams.set('offset',    String(filter.offset));
    if (filter.size)      url.searchParams.set('size',      String(filter.size));
    if (filter.sortBy)    url.searchParams.set('sortBy',    filter.sortBy);
    if (filter.sortOrder) url.searchParams.set('sortOrder', filter.sortOrder);
    return url;
};

export const useSearch = (filter: ExtensionFilter) => {
    const { service } = useContext(MainContext);

    return useInfiniteQuery({
        queryKey: [SEARCH_QUERY_KEY, filter.query, filter.category, filter.sortBy, filter.sortOrder, filter.size],
        queryFn: async ({ pageParam, signal }) => {
            const url = buildSearchUrl(service.serverUrl, { ...filter, offset: pageParam });
            return fetch(url, { signal }).then(res => res.json()) as Promise<SearchResult>;
        },
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) => {
            const loadedCount = allPages.flatMap(p => p.extensions).length;
            if (loadedCount < lastPage.totalSize && lastPage.extensions.length > 0) {
                return loadedCount;
            }
            return undefined;
        },
        retry: 3,
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 30000),
    });
};

export type UseSearchReturn = ReturnType<typeof useSearch>;
