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
import { MainContext } from '../../../context';
import { controllerFromSignal } from '../../../query-client';

export const adminStatisticsQueryKey = (year: number, month: number) => ['admin', 'statistics', year, month] as const;

/**
 * Loads the registry statistics for one month.
 *
 * A month that ended without the archival job running has no data and never will, so the server
 * answers 404 - the page presents that as "not archived" rather than as a failure. The month in
 * progress is always available, computed on request.
 */
export const useAdminStatistics = (year: number, month: number) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: adminStatisticsQueryKey(year, month),
        queryFn: ({ signal }) => service.admin.getAdminStatistics(controllerFromSignal(signal), year, month),
        // Computing the current month runs a handful of aggregate queries, so don't re-run it on
        // every remount while an admin clicks around.
        staleTime: 60_000,
        retry: false
    });
};
