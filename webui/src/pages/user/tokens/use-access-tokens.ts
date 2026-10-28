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
import { useReportedQuery } from '../../../hooks/use-reported-query';
import { PersonalAccessToken, UserData } from '../../../extension-registry-types';
import { NO_CACHE } from '../../../query-client';

const ACCESS_TOKENS_QUERY_KEY = ['user', 'tokens'];

/** Loads the current user's personal access tokens. */
export const useAccessTokens = () => {
    const { service, user } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ACCESS_TOKENS_QUERY_KEY,
            queryFn: ({ signal }) => service.getAccessTokens(signal, user!),
            enabled: user != null,
            ...NO_CACHE
        }),
        { action: 'load your access tokens' }
    );
};

export interface CreateAccessTokenRequest {
    user: UserData;
    description: string;
    scope?: { namespace?: string; extension?: string; publishingOnly?: boolean };
}

/** Generates a personal access token; on success the token list refreshes. */
export const useCreateAccessToken = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: ({ user, description, scope }: CreateAccessTokenRequest) =>
                service.createAccessToken(user, description, scope),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ACCESS_TOKENS_QUERY_KEY });
            }
        }),
        { action: 'generate the access token' }
    );
};

/** Revokes one personal access token; on success the token list refreshes. */
export const useDeleteAccessToken = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: (token: PersonalAccessToken) => service.deleteAccessToken(token),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ACCESS_TOKENS_QUERY_KEY });
            }
        }),
        { action: 'revoke the access token' }
    );
};

/** Revokes every given personal access token; on success the token list refreshes. */
export const useDeleteAllAccessTokens = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: (tokens: PersonalAccessToken[]) => service.deleteAllAccessTokens(tokens),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ACCESS_TOKENS_QUERY_KEY });
            }
        }),
        { action: 'revoke your access tokens' }
    );
};
