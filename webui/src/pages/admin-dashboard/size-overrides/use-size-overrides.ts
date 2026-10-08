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
import type { SizeOverride } from '../../../extension-registry-types';
import { controllerFromSignal } from '../../../query-client';

export const sizeOverridesQueryKey = ['admin', 'sizeOverrides'] as const;

export const useSizeOverrides = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: sizeOverridesQueryKey,
        queryFn: ({ signal }) => service.admin.getSizeOverrides(controllerFromSignal(signal))
    });
};

export const useCreateSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (override: SizeOverride) => service.admin.createSizeOverride(override),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey })
    });
};

export const useUpdateSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, override }: { id: number; override: SizeOverride }) =>
            service.admin.updateSizeOverride(id, override),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey })
    });
};

export const useDeleteSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: number) => service.admin.deleteSizeOverride(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey })
    });
};
