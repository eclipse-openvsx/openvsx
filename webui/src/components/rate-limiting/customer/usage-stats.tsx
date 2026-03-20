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
import {
    Typography,
    Paper,
    type PaperProps,
    Divider,
} from '@mui/material';
import type { Customer, UsageStats as UsageStatsType } from '../../../extension-registry-types';
import type { DateTime } from 'luxon';
import { UsageStatsChart } from '../usage-stats/usage-stats-chart';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export interface UsageStatsProps {
    usageStats: readonly UsageStatsType[];
    customer: Customer;
    startDate: DateTime;
    onStartDateChange: (date: DateTime) => void;
    compact?: boolean;
}

export const UsageStats: FC<UsageStatsProps> = ({ usageStats, customer, startDate, onStartDateChange, compact }) => (
    <Paper {...sectionPaperProps}>
        <Typography variant='h6' gutterBottom>
            Usage Statistics
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <UsageStatsChart
            usageStats={usageStats}
            customer={customer}
            startDate={startDate}
            onStartDateChange={onStartDateChange}
            embedded
            compact={compact}
        />
    </Paper>
);
