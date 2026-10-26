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
import { queryOptions, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { ExtensionRegistryService } from '../../../extension-registry-service';
import { useReportedQuery } from '../../../hooks/use-reported-query';
import { TrustedPublisher, TrustedPublisherRequest, UrlString } from '../../../extension-registry-types';

export const trustedPublisherKeys = {
    // the status (and with it the provider list) is global, not namespace-scoped
    status: ['user', 'trusted-publishing-status'] as const,
    // keyed by the namespace's trusted-publishing URL (the endpoint we actually query)
    publishers: (trustedPublishingUrl?: UrlString) => ['user', 'trusted-publishers', trustedPublishingUrl] as const
};

/**
 * Trusted-publishing status for the current user: whether the feature is enabled on the
 * registry, whether this user may use it, and the supported providers (present only when
 * allowed). The endpoint requires a logged-in user, so the query idles without one; idle
 * or failed queries leave `data` undefined, so feature gates reading it fail closed.
 */
export const useTrustedPublishingStatus = () => {
    const { service, user } = useContext(MainContext);
    return useQuery({
        queryKey: trustedPublisherKeys.status,
        queryFn: ({ signal }) => service.getTrustedPublishingStatus(signal),
        enabled: user != null
    });
};

const trustedPublishersQuery = (service: ExtensionRegistryService, trustedPublishingUrl?: UrlString) =>
    queryOptions({
        queryKey: trustedPublisherKeys.publishers(trustedPublishingUrl),
        queryFn: ({ signal }) => service.getTrustedPublishers(signal, trustedPublishingUrl!),
        enabled: Boolean(trustedPublishingUrl)
    });

export const useTrustedPublishers = (trustedPublishingUrl?: UrlString) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(useQuery(trustedPublishersQuery(service, trustedPublishingUrl)));
};

/**
 * The same list read as an ownership probe: it 403s unless the user owns the namespace, which
 * is an answer rather than a failure, so it is not reported.
 */
export const useTrustedPublishersProbe = (trustedPublishingUrl?: UrlString) => {
    const { service } = useContext(MainContext);
    return useQuery(trustedPublishersQuery(service, trustedPublishingUrl));
};

/**
 * The publishers of several namespaces flattened into one list. `registrableExtensions` stays
 * per namespace and is index-aligned with `trustedPublishingUrls`, because registering needs to
 * know which namespace an extension belongs to.
 */
export const useAllTrustedPublishers = (trustedPublishingUrls: UrlString[]) => {
    const { service } = useContext(MainContext);
    return useQueries({
        queries: trustedPublishingUrls.map(trustedPublishingUrl => ({
            queryKey: trustedPublisherKeys.publishers(trustedPublishingUrl),
            queryFn: ({ signal }: { signal: AbortSignal }) => service.getTrustedPublishers(signal, trustedPublishingUrl)
        })),
        combine: results => ({
            isLoading: results.some(result => result.isLoading),
            publishers: results.flatMap(result => result.data?.trustedPublishers ?? []) as TrustedPublisher[],
            registrableExtensions: results.map(result => result.data?.registrableExtensions ?? []) as string[][]
        })
    });
};

export const useRegisterTrustedPublisher = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({
            trustedPublishingUrl,
            request
        }: {
            trustedPublishingUrl: UrlString;
            request: TrustedPublisherRequest;
        }) => service.registerTrustedPublisher(trustedPublishingUrl, request),
        onSuccess: (_result, { trustedPublishingUrl }) => {
            queryClient.invalidateQueries({ queryKey: trustedPublisherKeys.publishers(trustedPublishingUrl) });
        }
    });
};

export const useDeleteTrustedPublisher = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: ({ trustedPublishingUrl, id }: { trustedPublishingUrl: UrlString; id: number }) =>
                service.deleteTrustedPublisher(trustedPublishingUrl, id),
            onSuccess: (_result, { trustedPublishingUrl }) => {
                queryClient.invalidateQueries({ queryKey: trustedPublisherKeys.publishers(trustedPublishingUrl) });
            }
        })
    );
};
