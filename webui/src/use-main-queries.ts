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
 * The current user, dropped once their session ends. A 401 on any query or mutation while someone is
 * logged in is checked against the registry's own answer for the current user: only if that says
 * nobody is logged in does `sessionExpired` get set. A visitor's 401 changes nothing.
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
    const sessionExpiredRef = useRef(false);
    const loggedInRef = useRef(false);
    const sessionCheck = useRef<Promise<boolean>>(undefined);

    // A user fetched again after the session ended, by logging in elsewhere, ends the expiry.
    if (sessionExpired && data !== undefined && !userError) {
        setSessionExpired(false);
    }
    useEffect(() => {
        if (!sessionExpired) {
            sessionExpiredRef.current = false;
        }
    }, [sessionExpired]);

    const loggedOut = isNotLoggedIn(userError) || sessionExpired;
    const user = loggedOut ? undefined : data;
    const loggedIn = user !== undefined;

    useEffect(() => {
        loggedInRef.current = loggedIn;
    }, [loggedIn]);

    /** Whether the session has ended, asking the registry once however many requests were refused. */
    const confirmSessionExpiry = useCallback((): Promise<boolean> => {
        if (sessionExpiredRef.current) {
            return Promise.resolve(true);
        }
        if (!loggedInRef.current) {
            return Promise.resolve(false);
        }
        sessionCheck.current ??= queryClient
            .fetchQuery({ queryKey: ['user'], queryFn: ({ signal }) => service.getUser(signal), staleTime: 0 })
            .then(
                () => false,
                error => isNotLoggedIn(error)
            )
            .then(ended => {
                sessionCheck.current = undefined;
                if (ended) {
                    sessionExpiredRef.current = true;
                    setSessionExpired(true);
                }
                return ended;
            });
        return sessionCheck.current;
    }, [queryClient, service]);

    useEffect(() => {
        if (!loggedIn) {
            return;
        }
        const onCacheEvent = (event: QueryCacheNotifyEvent | MutationCacheNotifyEvent) => {
            if (event.type === 'updated' && event.action.type === 'error' && isUnauthorized(event.action.error)) {
                void confirmSessionExpiry();
            }
        };
        const unsubscribeQueries = queryClient.getQueryCache().subscribe(onCacheEvent);
        const unsubscribeMutations = queryClient.getMutationCache().subscribe(onCacheEvent);
        return () => {
            unsubscribeQueries();
            unsubscribeMutations();
        };
    }, [loggedIn, queryClient, confirmSessionExpiry]);

    return { user, userError, userLoading, loggedOut, sessionExpired, refetchUser, confirmSessionExpiry };
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
