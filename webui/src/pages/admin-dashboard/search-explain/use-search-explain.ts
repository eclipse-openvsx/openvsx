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

import { useContext } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { controllerFromSignal } from '../../../query-client';

/**
 * Runs a search and reports where each result's score came from, a page at a time.
 * <p>
 * Paged rather than fetched whole because the question is often about a result a long way down - the one
 * that prompted this page sat at 767 - and every entry costs the server a lookup of the extension behind
 * it. Pages accumulate, so reaching a deep result does not mean refetching the shallow ones.
 * <p>
 * Only fetches once a term has been submitted: an empty query matches everything, and a listing of
 * everything in score order answers no question anybody asked.
 */
export const useSearchExplain = (query: string, size: number) => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: ['admin', 'search-explain', query, size] as const,
        queryFn: ({ signal, pageParam }) =>
            service.admin.explainSearch(controllerFromSignal(signal), query, size, pageParam),
        initialPageParam: 0,
        getNextPageParam: (lastPage, pages) => {
            const fetched = pages.reduce((count, page) => count + page.entries.length, 0);
            // A short page means the engine had no more to give, whatever the total says.
            return lastPage.entries.length < size || fetched >= lastPage.totalHits ? undefined : fetched;
        },
        enabled: query.length > 0
    });
};
