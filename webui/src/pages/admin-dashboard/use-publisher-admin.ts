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
import { useMutation, useQuery } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { controllerFromSignal } from '../../query-client';

export const publisherAdminKeys = {
    detail: (provider: string, login: string) => ['admin', 'publisher', provider, login] as const
};

// Shared prefix for the publisher write operations so a single `useIsMutating`
// can tell whether any of them (revokes, forget) is currently in flight.
export const publisherMutationKey = ['admin', 'publisher-mutation'] as const;

export const usePublisherInfo = (login: string, provider = 'github', enabled = true) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: publisherAdminKeys.detail(provider, login),
        queryFn: ({ signal }) => service.admin.getPublisherInfo(controllerFromSignal(signal), provider, login),
        enabled: enabled && !!login,
        staleTime: 0
    });
};

/**
 * Revokes all contributions of a publisher.
 */
export const useRevokePublisherContributions = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationKey: [...publisherMutationKey, 'revoke-contributions'],
        mutationFn: ({ provider, login }: { provider: string; login: string }) =>
            service.admin.revokePublisherContributions(provider, login)
    });
};

/**
 * Revokes the access tokens of a publisher.
 */
export const useRevokeAccessTokens = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationKey: [...publisherMutationKey, 'revoke-tokens'],
        mutationFn: ({ provider, login }: { provider: string; login: string }) =>
            service.admin.revokeAccessTokens(provider, login)
    });
};

/**
 * Forgets a user in response to a data-protection erasure request.
 */
export const useForgetUser = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationKey: [...publisherMutationKey, 'forget-user'],
        mutationFn: ({ provider, login }: { provider: string; login: string }) =>
            service.admin.forgetUser(provider, login)
    });
};
