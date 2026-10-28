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

import { useContext, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import type { UsageStats } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { getDefaultStartDate } from '../../../components/rate-limiting/usage-stats/usage-stats-utils';
import { DateTime } from 'luxon';

// Stable empty reference so consumers don't see a new array identity on every render.
const NO_STATS: readonly UsageStats[] = [];

export const useAdminUsageStats = (customerName: string | undefined) => {
    const { service } = useContext(MainContext);
    const [startDate, setStartDate] = useState<DateTime>(getDefaultStartDate);

    const { data, isFetching, error } = useQuery({
        queryKey: ['admin', 'usage-stats', customerName ?? null, startDate.toMillis()],
        queryFn: ({ signal }) => service.admin.getUsageStats(signal, customerName!, startDate.toJSDate()),
        enabled: !!customerName
    });

    return {
        usageStats: data?.stats ?? NO_STATS,
        dailyP95: data?.dailyP95,
        loading: isFetching,
        error: error ? handleError(error as Error) : null,
        startDate,
        setStartDate
    };
};
