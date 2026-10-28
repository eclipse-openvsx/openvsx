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
import { NamespaceDetails } from '../../extension-registry-types';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { NO_CACHE } from '../../query-client';
import { isNotFound } from '../../server-request';

const detailsKey = (name: string) => ['namespace-details', name];

/**
 * Loads a namespace's public details. The details form and the logo sidebar subscribe
 * to the same cache entry, so a mutation below refreshes both. A 404 is left to the
 * namespace page, which renders it as not found.
 */
export const useNamespaceDetails = (name: string) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: detailsKey(name),
            queryFn: ({ signal }) => service.getNamespaceDetails(signal, name),
            ...NO_CACHE
        }),
        { action: 'load the namespace details', ignore: isNotFound }
    );
};

/** Updates a namespace's details; on success every details consumer refreshes. */
export const useUpdateNamespaceDetails = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: ({ detailsUrl, details }: { detailsUrl: string; details: NamespaceDetails }) =>
                service.setNamespaceDetails(detailsUrl, details),
            onSuccess: (_result, { details }) => {
                queryClient.invalidateQueries({ queryKey: detailsKey(details.name) });
            }
        }),
        { action: 'save the namespace details' }
    );
};

/** Uploads a namespace logo; on success every details consumer refreshes. */
export const useUpdateNamespaceLogo = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: ({
                detailsUrl,
                file,
                fileName
            }: {
                namespaceName: string;
                detailsUrl: string;
                file: Blob;
                fileName: string;
            }) => service.setNamespaceLogo(detailsUrl, file, fileName),
            onSuccess: (_result, { namespaceName }) => {
                queryClient.invalidateQueries({ queryKey: detailsKey(namespaceName) });
            }
        }),
        { action: 'update the namespace logo' }
    );
};
