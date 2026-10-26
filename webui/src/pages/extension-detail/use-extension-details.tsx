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
import { useQuery } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { Extension } from '../../extension-registry-types';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { isNotFound } from '../../server-request';

export const extensionDetailKeys = {
    /** Every target and version of an extension, as a review changes the rating they all show. */
    all: (namespace: string, name: string) => ['extension-detail', namespace, name],
    detail: (namespace: string, name: string, target?: string, version?: string) => [
        'extension-detail',
        namespace,
        name,
        target ?? null,
        version ?? null
    ]
};

/**
 * Loads an extension of the given target and version, or of the latest when none is given.
 * A 404 is left to the page, which renders it as a not-found state.
 */
export const useExtensionDetail = (namespace: string, name: string, target?: string, version?: string) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: extensionDetailKeys.detail(namespace, name, target, version),
            queryFn: ({ signal }) =>
                service.getExtensionDetail(signal, service.getExtensionApiUrl({ namespace, name, target, version })),
            enabled: !!namespace && !!name,
            // Publishing or deleting a version changes it from other pages, so each visit revalidates.
            staleTime: 0
        }),
        { ignore: isNotFound }
    );
};

/** Loads an extension version's README; disabled when the version has none. */
export const useExtensionReadme = (extension: Extension) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['extension-readme', extension.files.readme ?? null],
            queryFn: ({ signal }) => service.getExtensionReadme(signal, extension),
            enabled: !!extension.files.readme
        })
    );
};

/** Loads an extension version's changelog; disabled when the version has none. */
export const useExtensionChangelog = (extension: Extension) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['extension-changelog', extension.files.changelog ?? null],
            queryFn: ({ signal }) => service.getExtensionChangelog(signal, extension),
            enabled: !!extension.files.changelog
        })
    );
};
