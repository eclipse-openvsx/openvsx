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
import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { ExtensionRegistryService } from '../../extension-registry-service';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { isNotFound } from '../../server-request';

interface ChangeNamespaceRequest {
    oldNamespace: string;
    newNamespace: string;
    removeOldNamespace: boolean;
    mergeIfNewNamespaceAlreadyExists: boolean;
}

export const namespaceAdminKeys = {
    detail: (name: string) => ['admin', 'namespace', name] as const
};

/**
 * The admin lookup of a single namespace, shared by the admin search view and the size-override
 * form. `staleTime: 0` keeps each lookup fresh and `retry: false` lets a 404 surface immediately
 * as a "not found" result rather than being retried.
 */
export const namespaceAdminQuery = (service: ExtensionRegistryService, name: string) =>
    queryOptions({
        queryKey: namespaceAdminKeys.detail(name),
        queryFn: ({ signal }) => service.admin.getNamespace(signal, name),
        retry: false,
        staleTime: 0
    });

/**
 * Looks up a single namespace for the admin search view, which shows a 404 as "not found".
 */
export const useAdminNamespace = (name: string) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(useQuery({ ...namespaceAdminQuery(service, name), enabled: !!name }), {
        action: 'load the namespace',
        ignore: isNotFound
    });
};

/**
 * Creates a namespace and refreshes the matching lookup on success.
 */
export const useCreateNamespace = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: (name: string) => service.admin.createNamespace({ name }),
            onSuccess: (_result, name) => {
                queryClient.invalidateQueries({ queryKey: namespaceAdminKeys.detail(name) });
            }
        }),
        { action: 'create the namespace' }
    );
};

/**
 * Renames (and optionally merges) a namespace.
 */
export const useChangeNamespace = () => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useMutation({
            mutationFn: (req: ChangeNamespaceRequest) => service.admin.changeNamespace(req)
        }),
        { action: 'change the namespace' }
    );
};

/**
 * Deletes a namespace.
 */
export const useDeleteNamespace = () => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useMutation({
            mutationFn: (name: string) => service.admin.deleteNamespace({ name })
        }),
        { action: 'delete the namespace' }
    );
};

/**
 * Drops a namespace lookup from the cache, e.g. after it has been deleted so a
 * later search refetches instead of showing the removed namespace.
 */
export const useClearAdminNamespace = () => {
    const queryClient = useQueryClient();
    return (name: string) => {
        queryClient.removeQueries({ queryKey: namespaceAdminKeys.detail(name) });
    };
};
