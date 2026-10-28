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

const searchIndexQueryKey = ['admin', 'search-index'] as const;

/**
 * The state of the search index: which engine answers searches, and how much it holds against how much
 * it is built from.
 */
export const useSearchIndex = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: searchIndexQueryKey,
        queryFn: ({ signal }) => service.admin.getSearchIndex(signal)
    });
};

/**
 * Rebuilds the index from scratch. The statistics are refetched afterwards, since the counts are the
 * only way to tell whether the rebuild achieved anything.
 */
export const useUpdateSearchIndex = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: () => service.admin.updateSearchIndex(),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: searchIndexQueryKey })
    });
};

export const useRefreshSearchIndex = () => {
    const queryClient = useQueryClient();
    return () => queryClient.invalidateQueries({ queryKey: searchIndexQueryKey });
};
