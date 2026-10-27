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
import { useMutation, useQuery, UseQueryOptions } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { ReportedError } from '../../../extension-registry-types';
import { useReportedQuery } from '../../../hooks/use-reported-query';

/** The publisher agreement's Markdown text, read from `PageSettings.urls.publisherAgreement`. */
export const usePublisherAgreementText = (
    url: string,
    options?: Omit<UseQueryOptions<string>, 'queryKey' | 'queryFn'>
) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['publisher-agreement', url],
            queryFn: ({ signal }) => service.getStaticContent(signal, url),
            ...options
        }),
        { action: 'load the publisher agreement' }
    );
};

/** Signs the publisher agreement; the signed status arrives with the refreshed user. */
export const useSignPublisherAgreement = () => {
    const { service, updateUser } = useContext(MainContext);
    return useReportedQuery(
        useMutation({
            mutationFn: async () => {
                try {
                    return await service.signPublisherAgreement();
                } catch (err) {
                    // Tagged so the error dialog says whom to contact about a failed signing.
                    if (!(err as ReportedError).code) {
                        Object.assign(err as object, { code: 'publisher-agreement-problem' });
                    }
                    throw err;
                }
            },
            onSuccess: () => updateUser()
        }),
        { action: 'sign the publisher agreement' }
    );
};
