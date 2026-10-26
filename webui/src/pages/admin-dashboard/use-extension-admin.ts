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
import { useReportedQuery } from '../../hooks/use-reported-query';
import { isNotFound } from '../../server-request';

interface ExtensionTarget {
    namespace: string;
    extension: string;
}

interface DeleteExtensionRequest {
    namespace: string;
    extension: string;
    targetPlatformVersions?: object[];
}

/**
 * Looks up an extension for the admin search view, which shows a 404 as "not found".
 * `staleTime: 0` keeps each lookup fresh and `retry: false` lets a 404 surface immediately.
 * Pass `null` until the user triggers a (validated) search.
 */
export const useAdminExtension = (target: ExtensionTarget | null) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['admin', 'extension', target?.namespace ?? '', target?.extension ?? ''],
            queryFn: ({ signal }) => service.admin.getExtension(signal, target!.namespace, target!.extension),
            enabled: !!target,
            retry: false,
            staleTime: 0
        }),
        { ignore: isNotFound }
    );
};

/**
 * Deletes extension versions. Not reported here: the version dialogs handle a failure themselves.
 */
export const useDeleteExtension = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationFn: (req: DeleteExtensionRequest) => service.admin.deleteExtensions(req)
    });
};

/**
 * Permanently purges extension versions (admin only). Unlike delete, this physically removes the
 * versions from the database and storage, freeing their identities for republishing. Not reported
 * here: the version dialogs handle a failure themselves.
 */
export const usePurgeExtension = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationFn: (req: DeleteExtensionRequest) => service.admin.purgeExtensions(req)
    });
};
