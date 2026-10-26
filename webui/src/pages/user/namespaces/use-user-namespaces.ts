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
import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { useReportedQuery } from '../../../hooks/use-reported-query';
import { NO_CACHE } from '../../../query-client';
import { createRoute } from '../../../utils';
import { UserSettingsRoutes } from '../user-settings-routes';

const NAMESPACES_QUERY_KEY = ['user', 'namespaces'];

/**
 * Loads the namespaces the current user is a member of. Shared by the settings
 * sidebar and the namespaces tab, which subscribe to the same cache entry.
 */
export const useUserNamespaces = () => {
    const { service, user } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: NAMESPACES_QUERY_KEY,
            queryFn: ({ signal }) => service.getNamespaces(signal),
            enabled: user != null,
            ...NO_CACHE
        })
    );
};

/**
 * Claims a namespace for the current user. Also reached from publishing, where a first-time
 * publisher's namespace does not exist yet, so the list is refreshed here rather than by each caller.
 * Not reported here: publishing reports its own failures and would show this one twice.
 */
export const useCreateNamespace = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (name: string) => service.createNamespace(name),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: NAMESPACES_QUERY_KEY })
    });
};

/** `CreateNamespaceDialog` callback that opens the namespace just created. */
export const useHandleNamespaceCreated = () => {
    const navigate = useNavigate();
    return (name: string) => navigate(createRoute([UserSettingsRoutes.NAMESPACES, name]));
};
