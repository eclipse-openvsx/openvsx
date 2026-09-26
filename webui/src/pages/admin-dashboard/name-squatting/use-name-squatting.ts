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
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import type {
    NameSquattingActionResponse,
    NameSquattingState,
    NameSquattingTarget
} from '../../../extension-registry-types';
import { controllerFromSignal } from '../../../query-client';

export interface NameSquattingFilters {
    publisher?: string;
    namespace?: string;
    name?: string;
    state?: NameSquattingState[];
}

export const nameSquattingKeys = {
    all: ['admin', 'name-squatting'] as const,
    list: (filters: NameSquattingFilters, offset: number, size: number) =>
        ['admin', 'name-squatting', 'list', filters, offset, size] as const,
    counts: (filters: NameSquattingFilters) => ['admin', 'name-squatting', 'counts', filters] as const
};

/**
 * Loads a page of extensions flagged by the name squatting check. `keepPreviousData` keeps the
 * current rows on screen while a new page or a changed filter is fetched.
 */
export const useNameSquattingFlags = (filters: NameSquattingFilters, offset: number, size: number) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: nameSquattingKeys.list(filters, offset, size),
        queryFn: ({ signal }) =>
            service.admin.getNameSquattingFlags(controllerFromSignal(signal), { ...filters, offset, size }),
        placeholderData: keepPreviousData
    });
};

/**
 * Loads the number of flagged extensions per state. The state filter is deliberately not passed on,
 * so the counts stay stable while the administrator switches between states.
 */
export const useNameSquattingCounts = (filters: NameSquattingFilters) => {
    const { service } = useContext(MainContext);
    const { publisher, namespace, name } = filters;
    return useQuery({
        queryKey: nameSquattingKeys.counts({ publisher, namespace, name }),
        queryFn: ({ signal }) =>
            service.admin.getNameSquattingCounts(controllerFromSignal(signal), { publisher, namespace, name })
    });
};

/**
 * Marks the findings for one or more extensions as a false positive, clearing the check error.
 */
export const useClearNameSquattingFlags = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (targets: NameSquattingTarget[]) =>
            failOnRejectedTargets(await service.admin.clearNameSquattingFlags({ targets })),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: nameSquattingKeys.all })
    });
};

/**
 * Soft-deletes one or more flagged extensions, making them unavailable while keeping their records.
 */
export const useDeleteNameSquattingExtensions = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (targets: NameSquattingTarget[]) =>
            failOnRejectedTargets(await service.admin.deleteNameSquattingExtensions({ targets })),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: nameSquattingKeys.all })
    });
};

/**
 * The moderation endpoints answer 200 with per-extension outcomes, so a request that changed
 * nothing has to be turned into a rejection for the caller's error path to see it.
 */
const failOnRejectedTargets = (response: Readonly<NameSquattingActionResponse>): NameSquattingActionResponse => {
    if (response.successful === 0 && response.failed > 0) {
        throw new Error(response.results.find(result => result.error)?.error ?? 'The action could not be applied');
    }
    return response;
};
