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
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { AdminPermission } from '../../../extension-registry-types';

export type AccessRole = 'admin' | 'privileged' | 'none';

// Shared prefix for the access-control write operations so a single `useIsMutating`
// can tell whether either of them (role change, permission grant/revoke) is in flight.
export const accessControlMutationKey = ['admin', 'access-control-mutation'] as const;

/**
 * Updates a user's global role and refreshes the shared admin user search on success so the
 * new role is reflected wherever that user is listed (here and on Publisher admin).
 */
export const useUpdateUserRole = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationKey: [...accessControlMutationKey, 'role'],
        mutationFn: ({ provider, login, role }: { provider: string; login: string; role: AccessRole }) =>
            service.admin.updateUserRole(provider, login, role),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'user-search'] });
        }
    });
};

/**
 * Grants or revokes a single permission for a user.
 */
export const useUpdateUserPermission = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationKey: [...accessControlMutationKey, 'permission'],
        mutationFn: ({
            provider,
            login,
            permission,
            grant
        }: {
            provider: string;
            login: string;
            permission: AdminPermission;
            grant: boolean;
        }) => service.admin.updateUserPermission(provider, login, permission, grant),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'user-search'] });
        }
    });
};
