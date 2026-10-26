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
import { MainContext } from '../../../context';
import { useReportedQuery } from '../../../hooks/use-reported-query';
import { NO_CACHE } from '../../../query-client';

interface UserExtensionTarget {
    namespace: string;
    extension: string;
}

interface DeleteUserExtensionRequest {
    namespace: string;
    extension: string;
    targetPlatformVersions?: object[];
}

/**
 * Loads one of the current user's extensions for the extension settings page.
 */
export const useUserExtension = (target: UserExtensionTarget) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['user', 'extension', target.namespace, target.extension],
            queryFn: ({ signal }) => service.getExtension(signal, target.namespace, target.extension),
            ...NO_CACHE
        })
    );
};

/**
 * Deletes extension versions. Not reported here: the delete dialogs it is handed to report
 * its failures, a partial delete included.
 */
export const useDeleteUserExtensionVersions = () => {
    const { service } = useContext(MainContext);
    return useMutation({
        mutationFn: (req: DeleteUserExtensionRequest) => service.deleteExtensions(req)
    });
};
