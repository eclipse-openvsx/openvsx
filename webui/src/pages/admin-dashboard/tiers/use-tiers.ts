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
import type { Tier } from '../../../extension-registry-types';

export const tiersQueryKey = ['admin', 'tiers'] as const;

/**
 * Loads all rate-limit tiers.
 */
export const useTiers = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: tiersQueryKey,
        queryFn: ({ signal }) => service.admin.getTiers(signal)
    });
};

/**
 * Creates a tier and refreshes the tier list on success.
 */
export const useCreateTier = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (tier: Tier) => service.admin.createTier(tier),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: tiersQueryKey });
        }
    });
};

/**
 * Updates an existing tier and refreshes the tier list on success.
 */
export const useUpdateTier = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ name, tier }: { name: string; tier: Tier }) => service.admin.updateTier(name, tier),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: tiersQueryKey });
        }
    });
};

/**
 * Deletes a tier and refreshes the tier list on success.
 */
export const useDeleteTier = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (name: string) => service.admin.deleteTier(name),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: tiersQueryKey });
        }
    });
};
