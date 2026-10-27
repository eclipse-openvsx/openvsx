/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
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

import { FunctionComponent, useEffect, useMemo, useRef } from 'react';
import { ExtensionGrid } from './page-primitives';
import { ExtensionCard } from './extension-card';
import { ExtensionListLoadError } from './extension-list-load-error';
import { ExtensionFilter } from '../extension-registry-service';
import { useExtensionResultsCursor } from '../hooks/use-extension-results-cursor';
import { useInfiniteSearch } from '../hooks/use-infinite-search';
import { useInView } from '../hooks/use-in-view';

export const ExtensionList: FunctionComponent<ExtensionListProps> = ({ filter, onUpdate }) => {
    const {
        data,
        isError,
        isLoading,
        isFetching,
        isFetchingNextPage,
        isFetchNextPageError,
        hasNextPage,
        fetchNextPage,
        refetch
    } = useInfiniteSearch(filter);
    const sentinelRef = useRef<HTMLDivElement>(null);
    const nearEnd = useInView(sentinelRef, { once: false, rootMargin: '200px', enabled: hasNextPage });

    const extensions = useMemo(() => data?.pages.flatMap(page => page.extensions) ?? [], [data]);
    const totalSize = data?.pages[0]?.totalSize ?? 0;
    const grid = useExtensionResultsCursor(extensions.length);

    // Report the result count to the parent (shown in the search header).
    useEffect(() => {
        onUpdate(totalSize);
    }, [totalSize, onUpdate]);

    // Fresh filter: put the cursor back on the first card so Enter opens it.
    useEffect(() => {
        grid.reset();
    }, [filter.query, filter.category, filter.sortBy, filter.sortOrder, grid.reset]);

    const pageSize = filter.size;
    // Retrying a failed first page is not `isLoading` (the query stays in error), hence `!data`.
    const loading = isLoading || isFetchingNextPage || (isFetching && !data);
    // Load failures show inline rather than in the error dialog; a failed refetch keeps its results.
    const firstPageFailed = isError && !data && !isFetching;
    const nextPageFailed = isFetchNextPageError && !isFetching;

    // Cards already in the query cache when this list mounts (e.g. returning via
    // browser back) render in place without replaying the entrance fade, so the
    // page looks as if it was never unmounted. Cards that load afterwards still fade.
    const restoredCount = useRef(loading ? 0 : extensions.length).current;

    // Index-keyed slots (the list only appends): loading slots render as
    // skeletons that become cards in place, so the fade isn't restarted.
    const slotCount = extensions.length + (loading ? pageSize : 0);
    const cards = Array.from({ length: slotCount }, (_, idx) => {
        const extension = extensions[idx];
        return (
            <ExtensionCard
                key={idx}
                extension={extension}
                appear={idx >= restoredCount}
                fadeDelayMs={Math.min(idx % pageSize, 5) * 200}
                {...(extension ? grid.itemProps(idx) : {})}
            />
        );
    });

    // The observer only reports changes, so re-check whenever a page lands: a sentinel still in
    // view must trigger the next one. Waiting out any fetch avoids cancelling it. A failed page
    // leaves nothing here changed, so it waits for the retry button instead of looping.
    const loadedCount = extensions.length;
    useEffect(() => {
        if (nearEnd && hasNextPage && !isFetching && !isFetchNextPageError) {
            void fetchNextPage();
        }
    }, [nearEnd, hasNextPage, isFetching, isFetchNextPageError, loadedCount, fetchNextPage]);

    return (
        <div>
            <ExtensionGrid {...grid.containerProps}>{cards}</ExtensionGrid>
            {firstPageFailed && (
                <ExtensionListLoadError message='We couldn’t load extensions at this time.' onRetry={() => refetch()} />
            )}
            {nextPageFailed && (
                <ExtensionListLoadError
                    showDivider
                    message='We couldn’t load more extensions at this time.'
                    onRetry={() => fetchNextPage()}
                />
            )}
            <div ref={sentinelRef} />
        </div>
    );
};

export interface ExtensionListProps {
    filter: ExtensionFilter;
    onUpdate: (resultNumber: number) => void;
}
