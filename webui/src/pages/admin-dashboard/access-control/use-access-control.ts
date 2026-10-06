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
import { UserAccess } from '../../../extension-registry-types';

export type AccessRole = UserAccess['role'];

/**
 * Replaces a user's role and permissions in one request, and refreshes the shared admin user
 * search on success so the new access is reflected wherever that user is listed (here and on
 * Publisher admin).
 */
export const useUpdateUserAccess = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ provider, login, access }: { provider: string; login: string; access: UserAccess }) =>
            service.admin.updateUserAccess(provider, login, access),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'user-search'] });
        }
    });
};
