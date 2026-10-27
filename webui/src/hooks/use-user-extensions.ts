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
import { queryOptions, useQuery } from '@tanstack/react-query';
import { MainContext } from '../context';
import { ExtensionRegistryService } from '../extension-registry-service';
import { useReportedQuery } from './use-reported-query';

export const USER_EXTENSIONS_QUERY_KEY = ['user', 'extensions'];

/**
 * The extensions the current user has published. Written as options rather than only a hook because
 * the publish queue reads the same cache entry while it follows a package, which keeps the settings
 * list in step with what the queue learns.
 */
export const userExtensionsQuery = (service: ExtensionRegistryService) =>
    queryOptions({
        queryKey: USER_EXTENSIONS_QUERY_KEY,
        queryFn: ({ signal }) => service.getExtensions(signal)
    });

/** Lists the current user's published extensions; idle until there is a user to list them for. */
export const useUserExtensions = () => {
    const { service, user } = useContext(MainContext);
    return useReportedQuery(useQuery({ ...userExtensionsQuery(service), enabled: user != null }), {
        action: 'load your extensions'
    });
};
