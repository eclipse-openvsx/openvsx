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
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';

const analyticsStatusQueryKey = ['admin', 'analytics', 'status'] as const;

// An outage is what this page is opened for, so it keeps itself current instead of waiting to be refreshed.
const REFRESH_INTERVAL_MS = 15_000;

/** Whether download analytics is on and reachable, and what is waiting for it. */
export const useAnalyticsStatus = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: analyticsStatusQueryKey,
        queryFn: ({ signal }) => service.admin.getAnalyticsStatus(signal),
        refetchInterval: REFRESH_INTERVAL_MS
    });
};

export const useRefreshAnalyticsStatus = () => {
    const queryClient = useQueryClient();
    return () => queryClient.invalidateQueries({ queryKey: analyticsStatusQueryKey });
};
