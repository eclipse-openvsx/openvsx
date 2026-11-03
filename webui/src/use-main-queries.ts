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

import { useCallback, useEffect, useRef, useState } from 'react';
import {
    useQuery,
    useQueryClient,
    type MutationCacheNotifyEvent,
    type QueryCacheNotifyEvent
} from '@tanstack/react-query';
import { ExtensionRegistryService } from './extension-registry-service';
import { ErrorResponse, isUnauthorized } from './server-request';

// `Main` builds MainContext from these, so they take the service as an argument.

/**
 * The current user, dropped once their session ends: any query or mutation refused with a 401 while
 * someone is logged in sets `sessionExpired`. A visitor's 401 changes nothing.
 */
export const useCurrentUser = (service: ExtensionRegistryService) => {
    const queryClient = useQueryClient();
    const {
        data,
        error: userError,
        isLoading: userLoading,
        refetch: refetchUser
    } = useQuery({ queryKey: ['user'], queryFn: ({ signal }) => service.getUser(signal) });
    const [sessionExpired, setSessionExpired] = useState(false);
    // Set by the cache listener before the refused request's error reaches whoever reports it.
    const sessionExpiredRef = useRef(false);

    const loggedOut = isNotLoggedIn(userError) || sessionExpired;
    const user = loggedOut ? undefined : data;
    const loggedIn = user !== undefined;

    useEffect(() => {
        if (!loggedIn) {
            return;
        }
        const onCacheEvent = (event: QueryCacheNotifyEvent | MutationCacheNotifyEvent) => {
            if (event.type === 'updated' && event.action.type === 'error' && isUnauthorized(event.action.error)) {
                sessionExpiredRef.current = true;
                setSessionExpired(true);
            }
        };
        const unsubscribeQueries = queryClient.getQueryCache().subscribe(onCacheEvent);
        const unsubscribeMutations = queryClient.getMutationCache().subscribe(onCacheEvent);
        return () => {
            unsubscribeQueries();
            unsubscribeMutations();
        };
    }, [loggedIn, queryClient]);

    /** Whether `error` is a 401 the expired session already explains. */
    const isSessionExpiryError = useCallback(
        (error: unknown) => sessionExpiredRef.current && isUnauthorized(error),
        []
    );

    return { user, userError, userLoading, loggedOut, sessionExpired, refetchUser, isSessionExpiryError };
};

/** Whether {@link useCurrentUser} failed because nobody is logged in, which the registry answers with a 200 error result. */
export const isNotLoggedIn = (error: unknown): boolean => (error as Partial<ErrorResponse> | null)?.status === 200;

/** `data` is `null` when the registry has no login providers configured. */
export const useLoginProviders = (service: ExtensionRegistryService, options: { enabled: boolean }) =>
    useQuery({
        queryKey: ['login-providers'],
        queryFn: ({ signal }) => service.getLoginProviders(signal),
        select: result => ('loginProviders' in result ? result.loginProviders : null),
        ...options
    });

/** Fails with the error the last login failed with, kept by the registry when it redirects back with `?auth-error`. */
export const useAuthError = (service: ExtensionRegistryService, options: { enabled: boolean }) =>
    useQuery({ queryKey: ['auth-error'], queryFn: ({ signal }) => service.getUserAuthError(signal), ...options });

export const useRegistryVersion = (service: ExtensionRegistryService) =>
    useQuery({ queryKey: ['version'], queryFn: ({ signal }) => service.getRegistryVersion(signal) });
