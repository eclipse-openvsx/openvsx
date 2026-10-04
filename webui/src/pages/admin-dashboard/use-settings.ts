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
import { MainContext } from '../../context';
import type { Settings } from '../../extension-registry-types';
import { controllerFromSignal } from '../../query-client';

export const settingsQueryKey = ['admin', 'settings'] as const;

/**
 * Loads the runtime settings managed in the admin dashboard.
 */
export const useSettings = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: settingsQueryKey,
        queryFn: ({ signal }) => service.admin.getSettings(controllerFromSignal(signal))
    });
};

/**
 * Persists runtime settings. On success the authoritative response from the
 * server is written straight into the cache, so the settings query updates
 * without an extra round-trip.
 */
export const useUpdateSettings = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (settings: Partial<Settings>) => service.admin.updateSettings(settings),
        onSuccess: updated => {
            queryClient.setQueryData(settingsQueryKey, updated);
        }
    });
};
