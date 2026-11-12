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
import { InfiniteData, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import type {
    NameSquattingAllowlist,
    NameSquattingAllowlistGroup,
    NameSquattingAllowRequest,
    NameSquattingCollisions,
    NameSquattingExtension,
    NameSquattingGroup,
    NameSquattingNamespaceAllowlist,
    NameSquattingNamespaceAllowRequest,
    NameSquattingNamespacePair,
    NameSquattingNamespacePairs,
    NameSquattingNamespaceSort,
    TargetPlatformVersion
} from '../../../extension-registry-types';

/** Candidates the server looks up per batch; each costs it a few trigram probes. */
export const COLLISIONS_BATCH_SIZE = 25;
export const ALLOWLIST_PAGE_SIZE = 25;
export const NAMESPACE_PAIRS_PAGE_SIZE = 25;

export const nameSquattingKeys = {
    all: ['admin', 'name-squatting'] as const,
    collisions: () => [...nameSquattingKeys.all, 'collisions'] as const,
    allowlist: () => [...nameSquattingKeys.all, 'allowlist'] as const,
    namespaces: (view: NamespacePairsView) =>
        [...nameSquattingKeys.all, 'namespaces', view.popularOnly, view.sort] as const,
    namespaceAllowlist: () => [...nameSquattingKeys.all, 'namespaces', 'allowlist'] as const
};

export interface NamespacePairsView {
    popularOnly: boolean;
    sort: NameSquattingNamespaceSort;
}

export const extensionId = (extension: Pick<NameSquattingExtension, 'namespace' | 'name'>): string =>
    `${extension.namespace}.${extension.name}`;

/** Joins the batches into one list; an anchor resembled in two batches is shown as one group. */
export const mergeCollisionPages = (data: InfiniteData<NameSquattingCollisions, number>): NameSquattingGroup[] => {
    const groups = new Map<string, NameSquattingGroup>();
    for (const group of data.pages.flatMap(page => page.groups)) {
        const key = extensionId(group.anchor);
        const existing = groups.get(key);
        if (!existing) {
            groups.set(key, { anchor: group.anchor, lookalikes: [...group.lookalikes] });
            continue;
        }
        const seen = new Set(existing.lookalikes.map(lookalike => extensionId(lookalike.extension)));
        existing.lookalikes.push(...group.lookalikes.filter(lookalike => !seen.has(extensionId(lookalike.extension))));
    }
    return [...groups.values()];
};

/** Joins the pages into one list; an allowed extension split across two pages is shown once. */
export const mergeAllowlistPages = (
    data: InfiniteData<NameSquattingAllowlist, number>
): NameSquattingAllowlistGroup[] => {
    const groups = new Map<string, NameSquattingAllowlistGroup>();
    for (const group of data.pages.flatMap(page => page.groups)) {
        const key = extensionId(group.extension);
        const existing = groups.get(key);
        if (!existing) {
            groups.set(key, { extension: group.extension, allows: [...group.allows] });
            continue;
        }
        const seen = new Set(existing.allows.map(allow => allow.id));
        existing.allows.push(...group.allows.filter(allow => !seen.has(allow.id)));
    }
    return [...groups.values()];
};

export const namespacePairKey = (pair: NameSquattingNamespacePair): string =>
    `${pair.target.name}/${pair.lookalike.name}`;

export interface NamespacePairList {
    pairs: NameSquattingNamespacePair[];
    total: number;
    popularTotal: number;
}

/**
 * Joins the pages into one list, the totals from the latest. The server looks the pairs up again once
 * its cached copy expires, which can shift a pair onto the next page; it is shown once.
 */
export const mergeNamespacePairPages = (data: InfiniteData<NameSquattingNamespacePairs, number>): NamespacePairList => {
    const pairs = new Map<string, NameSquattingNamespacePair>();
    for (const pair of data.pages.flatMap(page => page.pairs)) {
        const key = namespacePairKey(pair);
        if (!pairs.has(key)) {
            pairs.set(key, pair);
        }
    }
    const last = data.pages[data.pages.length - 1] as NameSquattingNamespacePairs | undefined;
    return { pairs: [...pairs.values()], total: last?.total ?? 0, popularTotal: last?.popularTotal ?? 0 };
};

/**
 * The look-alikes, looked up live a batch at a time. Never retried: a lookup that hit the server's
 * statement timeout would only be run again against the same database, and the page offers a retry.
 */
export const useNameSquattingCollisions = () => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: nameSquattingKeys.collisions(),
        queryFn: ({ signal, pageParam }) =>
            service.admin.getNameSquattingCollisions(signal, pageParam, COLLISIONS_BATCH_SIZE),
        initialPageParam: 0,
        getNextPageParam: lastPage => (lastPage.hasMore ? lastPage.nextOffset : undefined),
        select: mergeCollisionPages,
        retry: false
    });
};

export const useNameSquattingAllowlist = () => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: nameSquattingKeys.allowlist(),
        queryFn: ({ signal, pageParam }) =>
            service.admin.getNameSquattingAllowlist(signal, pageParam, ALLOWLIST_PAGE_SIZE),
        initialPageParam: 0,
        getNextPageParam: lastPage => (lastPage.hasMore ? lastPage.page + 1 : undefined),
        select: mergeAllowlistPages
    });
};

/** Never retried, for the same reason as the collisions: a 503 means the lookup ran out of time. */
export const useNameSquattingNamespacePairs = (view: NamespacePairsView) => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: nameSquattingKeys.namespaces(view),
        queryFn: ({ signal, pageParam }) =>
            service.admin.getNameSquattingNamespacePairs(
                signal,
                view.popularOnly,
                view.sort,
                pageParam,
                NAMESPACE_PAIRS_PAGE_SIZE
            ),
        initialPageParam: 0,
        getNextPageParam: lastPage => (lastPage.hasMore ? lastPage.offset + lastPage.pairs.length : undefined),
        select: mergeNamespacePairPages,
        retry: false
    });
};

export const useNameSquattingNamespaceAllowlist = () => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: nameSquattingKeys.namespaceAllowlist(),
        queryFn: ({ signal, pageParam }) =>
            service.admin.getNameSquattingNamespaceAllowlist(signal, pageParam, ALLOWLIST_PAGE_SIZE),
        initialPageParam: 0,
        getNextPageParam: (lastPage: NameSquattingNamespaceAllowlist) =>
            lastPage.hasMore ? lastPage.page + 1 : undefined,
        // An allow removed meanwhile shifts the next page back by one, repeating its first entry.
        select: data => [...new Map(data.pages.flatMap(page => page.allows).map(allow => [allow.id, allow])).values()]
    });
};

/** Both views of the Namespaces tab and the allowlist change, so every name squatting query is refreshed. */
export const useAllowNameSquattingNamespaces = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (request: NameSquattingNamespaceAllowRequest) =>
            service.admin.allowNameSquattingNamespaces(request),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: nameSquattingKeys.all })
    });
};

/**
 * Allows pairs one after another. Settling rather than succeeding refreshes both lists, so pairs
 * allowed before a failure leave the Collisions tab too.
 */
export const useAllowNameSquattingPairs = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (requests: NameSquattingAllowRequest[]) => {
            for (const request of requests) {
                await service.admin.allowNameSquattingPair(request);
            }
        },
        onSettled: () => queryClient.invalidateQueries({ queryKey: nameSquattingKeys.all })
    });
};

/** Removes allows one after another; like allowing, refreshes both lists however far it got. */
export const useRemoveNameSquattingAllows = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (ids: number[]) => {
            for (const id of ids) {
                await service.admin.removeNameSquattingAllow(id);
            }
        },
        onSettled: () => queryClient.invalidateQueries({ queryKey: nameSquattingKeys.all })
    });
};

/** Soft-deletes the named versions of an extension, as the admin Extensions page does. */
export const useDeleteExtension = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (req: {
            namespace: string;
            extension: string;
            targetPlatformVersions: Pick<TargetPlatformVersion, 'version' | 'targetPlatform'>[];
        }) => service.admin.deleteExtensions(req),
        onSuccess: (_result, req) =>
            Promise.all([
                queryClient.invalidateQueries({ queryKey: nameSquattingKeys.collisions() }),
                queryClient.invalidateQueries({ queryKey: ['admin', 'extension', req.namespace, req.extension] })
            ])
    });
};
