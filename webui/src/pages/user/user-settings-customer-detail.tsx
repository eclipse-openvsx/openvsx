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

import { FC } from 'react';
import { Box, Typography } from '@mui/material';
import type { Customer } from '../../extension-registry-types';
import { useUsageStats } from '../../components/rate-limiting/usage-stats/use-usage-stats';
import { UsageStats } from '../../components/rate-limiting/customer';

export interface UserSettingsCustomerDetailProps {
    customer: Customer;
}

export const UserSettingsCustomerDetail: FC<UserSettingsCustomerDetailProps> = ({ customer }) => {
    const { usageStats, startDate, setStartDate } = useUsageStats(customer.name);

    return (
        <Box sx={{ flex: 5, pl: { xs: 0, lg: 2 } }}>
            <Typography variant='h4' sx={{ mb: 2 }}>{customer.name}</Typography>
            <UsageStats
                usageStats={usageStats}
                customer={customer}
                startDate={startDate}
                onStartDateChange={setStartDate}
                compact
            />
        </Box>
    );
};
