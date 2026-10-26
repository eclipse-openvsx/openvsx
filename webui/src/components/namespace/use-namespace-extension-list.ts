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
import { QueryFunction, QueryKey, useQueries, UseQueryResult } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { Extension, Namespace } from '../../extension-registry-types';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { NO_CACHE } from '../../query-client';
import { isNotFound } from '../../server-request';

/**
 * The query for a single extension of a namespace. The caller decides which endpoint to use, e.g. the
 * public registry API (active extensions only) or the admin API (also returns inactive/soft-deleted
 * extensions), and keys the query to match. It gets both the extension `name` and its metadata `url`.
 */
export type NamespaceExtensionQuery = (extension: { name: string; url: string }) => {
    queryKey: QueryKey;
    queryFn: QueryFunction<Readonly<Extension>>;
};

// `error` stays null until every query has settled, so a failure is reported once, and prefers a
// failure other than a 404, which a caller may leave out.
const combineExtensions = (results: UseQueryResult<Readonly<Extension>>[]) => {
    const isPending = results.some(result => result.isPending);
    const errors = results.flatMap(result => (result.error ? [result.error] : []));
    return {
        extensions: results.flatMap(result => (result.data ? [result.data] : [])),
        error: isPending ? null : (errors.find(error => !isNotFound(error)) ?? errors[0] ?? null),
        isPending
    };
};

/**
 * Loads every extension of a namespace, one query each, combined into the extensions that loaded.
 * Without an `extensionQuery` it reads the public registry API; `omitUnavailable` leaves a 404 unreported.
 */
export const useNamespaceExtensionList = (
    namespace: Namespace,
    options: { extensionQuery?: NamespaceExtensionQuery; omitUnavailable?: boolean } = {}
) => {
    const { service } = useContext(MainContext);
    const query: NamespaceExtensionQuery =
        options.extensionQuery ??
        (extension => ({
            queryKey: ['namespace-extension', extension.url],
            queryFn: ({ signal }) => service.getExtensionDetail(signal, extension.url)
        }));
    return useReportedQuery(
        useQueries({
            queries: Object.entries(namespace.extensions).map(([name, url]) => ({
                ...query({ name, url }),
                ...NO_CACHE
            })),
            combine: combineExtensions
        }),
        { ignore: options.omitUnavailable ? isNotFound : undefined }
    );
};
