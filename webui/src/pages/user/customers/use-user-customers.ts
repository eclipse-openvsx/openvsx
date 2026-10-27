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
import { useReportedQuery } from '../../../hooks/use-reported-query';
import { NO_CACHE } from '../../../query-client';

/**
 * Loads the rate-limiting customer groups the current user is a member of.
 * Shared by the settings navigation (which hides the tab when there are none)
 * and the Rate Limiting tab itself.
 */
export const useUserCustomers = () => {
    const { service, user } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['user', 'customers'],
            queryFn: ({ signal }) => service.getCustomers(signal),
            enabled: user != null,
            ...NO_CACHE
        }),
        { action: 'load your rate limiting customers' }
    );
};
