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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { controllerFromSignal } from '../../../query-client';

const cachesQueryKey = ['admin', 'caches'] as const;

/** Every cache of every cache manager, with whatever each implementation can report. */
export const useCaches = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: cachesQueryKey,
        queryFn: ({ signal }) => service.admin.getCaches(controllerFromSignal(signal))
    });
};

/**
 * Clears one cache, or every cache when called with nothing. The list is refetched afterwards,
 * because the entry counts are the only confirmation that anything was dropped.
 */
export const useClearCaches = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (cache?: { manager: string; name: string }) => service.admin.clearCaches(cache),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: cachesQueryKey })
    });
};

export const useRefreshCaches = () => {
    const queryClient = useQueryClient();
    return () => queryClient.invalidateQueries({ queryKey: cachesQueryKey });
};
