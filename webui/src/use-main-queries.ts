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

import { useQuery } from '@tanstack/react-query';
import { ExtensionRegistryService } from './extension-registry-service';
import { queryClient } from './query-client';
import { ErrorResponse } from './server-request';

// `Main` builds MainContext and mounts the QueryClientProvider below itself, so these
// queries take the service as an argument and name the client they run on.

export const useCurrentUser = (service: ExtensionRegistryService) =>
    useQuery({ queryKey: ['user'], queryFn: ({ signal }) => service.getUser(signal) }, queryClient);

/** Whether {@link useCurrentUser} failed because nobody is logged in, which the registry answers with a 200 error result. */
export const isNotLoggedIn = (error: unknown): boolean => (error as Partial<ErrorResponse> | null)?.status === 200;

/** `data` is `null` when the registry has no login providers configured. */
export const useLoginProviders = (service: ExtensionRegistryService, options: { enabled: boolean }) =>
    useQuery(
        {
            queryKey: ['login-providers'],
            queryFn: ({ signal }) => service.getLoginProviders(signal),
            select: result => ('loginProviders' in result ? result.loginProviders : null),
            ...options
        },
        queryClient
    );

/** Fails with the error the last login failed with, kept by the registry when it redirects back with `?auth-error`. */
export const useAuthError = (service: ExtensionRegistryService, options: { enabled: boolean }) =>
    useQuery(
        { queryKey: ['auth-error'], queryFn: ({ signal }) => service.getUserAuthError(signal), ...options },
        queryClient
    );

export const useRegistryVersion = (service: ExtensionRegistryService) =>
    useQuery({ queryKey: ['version'], queryFn: ({ signal }) => service.getRegistryVersion(signal) }, queryClient);
