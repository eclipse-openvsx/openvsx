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
import { DateTime } from 'luxon';
import { MainContext } from '../../context';
import { controllerFromSignal } from '../../query-client';
import { DownloadSeriesPoint } from '../../extension-registry-types';

const WEEKS = 52;

/**
 * Loads the last {@link WEEKS} whole weeks of *daily* downloads for an extension, ending today, as
 * the react-query result (`data` is the ordered {@link DownloadSeriesPoint} array). The range is an
 * exact multiple of 7 days so callers can fold it into whole weeks with nothing left over; daily
 * granularity is what lets those weeks end on today rather than on the last complete calendar week.
 * Gate with `options.enabled` on `RegistryVersion.analyticsEnabled`, since the endpoint 404s when
 * analytics is disabled.
 */
export const useExtensionDownloadSeries = (namespace: string, name: string, options?: { enabled?: boolean }) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['extension-downloads', namespace, name],
        queryFn: async ({ signal }): Promise<DownloadSeriesPoint[]> => {
            const today = DateTime.utc().startOf('day');
            // `to` is exclusive, so today + 1 day includes today's (still-accruing) bucket.
            const to = today.plus({ days: 1 });
            const from = to.minus({ weeks: WEEKS });
            const series = await service.getExtensionDownloadSeries(controllerFromSignal(signal), {
                namespace,
                name,
                from: from.toFormat('yyyy-MM-dd'),
                to: to.toFormat('yyyy-MM-dd'),
                interval: 'day'
            });
            return series.points;
        },
        ...options
    });
};
