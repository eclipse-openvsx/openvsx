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
import { DownloadSeriesPoint } from '../../extension-registry-types';

/**
 * Loads the last 52 whole weeks of *daily* downloads for an extension, ending today, as the
 * react-query result (`data` is the ordered {@link DownloadSeriesPoint} array). The server fixes
 * that window. It is an exact multiple of 7 days so callers can fold it into whole weeks with
 * nothing left over; daily granularity is what lets those weeks end on today rather than on the
 * last complete calendar week.
 * Gate with `options.enabled` on `SiteSettings.downloadAnalyticsVisible`, so the series is only
 * fetched where the card is shown.
 */
export const useExtensionDownloadSeries = (namespace: string, name: string, options?: { enabled?: boolean }) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['extension-downloads', namespace, name],
        queryFn: async ({ signal }): Promise<DownloadSeriesPoint[]> => {
            const series = await service.getExtensionDownloadSeries(signal, { namespace, name });
            return series.points;
        },
        ...options
    });
};
