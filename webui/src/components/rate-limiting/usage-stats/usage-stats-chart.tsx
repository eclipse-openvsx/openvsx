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

import { FC, useMemo } from "react";
import {
    Box,
    Paper,
    Typography,
    Alert,
    Stack,
    IconButton
} from "@mui/material";
import { BarPlot } from "@mui/x-charts/BarChart";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import type { Customer, UsageStats } from "../../../extension-registry-types";
import {
    ChartsReferenceLine,
    ChartsTooltip,
    ChartsXAxis,
    ChartsYAxis,
    ResponsiveChartContainer
} from "@mui/x-charts";
import { DateTime } from "luxon";

interface UsageStatsChartProps {
    usageStats: readonly UsageStats[];
    customer: Customer | null;
    startDate: DateTime;
    onStartDateChange: (date: DateTime) => void;
    embedded?: boolean;
    compact?: boolean;
}

export const UsageStatsChart: FC<UsageStatsChartProps> = ({
    usageStats,
    customer,
    startDate,
    onStartDateChange,
    embedded = false,
    compact = false
}) => {
    const dayStart = startDate.startOf('day').toMillis() / 1000;
    const dayEnd = startDate.endOf('day').toMillis() / 1000;

    // we have 5min steps
    const step = 5 * 60;
    const tierCapacity =
        customer?.tier !== undefined ? customer.tier.capacity * step / customer.tier.duration : 0;

    const data: UsageStats[] = useMemo(
        () => {
            const arr: UsageStats[] = [];

            for (let idx = dayStart; idx < dayEnd; idx += step) {
                arr.push({
                    windowStart: idx,
                    duration: step,
                    count: 0
                });
            }

            for (const stat of usageStats) {
                const idx = (stat.windowStart - dayStart) / step;
                if (idx >= 0 && idx < arr.length) {
                    arr[idx].count = stat.count;
                }
            }
            return arr;
        }, [usageStats]
    );

    const maxDataValue: number = useMemo(
        () => {
            if (usageStats.length === 0) {
                return 0;
            } else {
                return Math.max(...usageStats.map(v => v.count));
            }
        }, [usageStats]
    );

    const totalRequests = useMemo(
        () => usageStats.reduce((sum, d) => sum + d.count, 0),
        [usageStats]
    );

    const Wrapper: typeof Box = embedded ? Box : Paper;

    return (
        <LocalizationProvider dateAdapter={AdapterLuxon}>
            <Wrapper sx={{ p: 2, mb: embedded ? 2 : 3 }}>
                <Typography variant='subtitle2' gutterBottom color='text.secondary'>
                    Filters
                </Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems='center'>
                    <DatePicker
                        label='Start Date'
                        value={startDate}
                        onChange={onStartDateChange}
                        timezone='UTC'
                        slotProps={{ textField: { size: 'small' }, actionBar: { actions: ['today'] } }}
                    />
                    <IconButton size='small' onClick={() => onStartDateChange(startDate.minus({ days: 1 }))}>
                        <ChevronLeftIcon />
                    </IconButton>
                    <IconButton size='small' onClick={() => onStartDateChange(startDate.plus({ days: 1 }))}>
                        <ChevronRightIcon />
                    </IconButton>
                </Stack>
            </Wrapper>

            {usageStats.length === 0 ?
                <Alert severity='info'>No usage data available for this customer.</Alert>
             :
                <>
                <Wrapper sx={{ p: 2 }}>
                    <ResponsiveChartContainer
                        series={[{
                            type: 'bar',
                            data: data.map(d => d.count),
                            label: 'Request Count',
                            color: 'lightgray',
                        }]}

                        height={compact ? 300 : 400}
                        margin={{ top: 30 }}
                        xAxis={[
                            {
                                id: 'date',
                                data: data.map((value) => value.windowStart * 1000),
                                valueFormatter: (value) => DateTime.fromMillis(value).toLocaleString(DateTime.TIME_24_SIMPLE),
                                scaleType: 'band',
                            },
                        ]}
                        yAxis={[
                            {
                                id: 'requests',
                                scaleType: 'linear',
                                min: 0,
                                max: Math.max(tierCapacity, maxDataValue) + 30
                            },
                        ]}
                    >
                        <BarPlot />

                        {tierCapacity > 0 &&
                            <ChartsReferenceLine
                                y={tierCapacity}
                                label='Tier Limit'
                                labelAlign='end'
                                lineStyle={{
                                    strokeDasharray: '10 5',
                                    strokeWidth: 2,
                                    stroke: '#a83244',
                                }}
                            />
                        }

                        <ChartsXAxis
                            label='Time (UTC)'
                            position='bottom'
                            axisId='date'
                            tickInterval={(value) => {
                                const d = new Date(value);
                                return d.getMinutes() === 0 && (!compact || d.getHours() % 3 === 0);
                            }}
                            tickLabelInterval={(value) => {
                                const d = new Date(value);
                                return d.getMinutes() === 0 && (!compact || d.getHours() % 3 === 0);
                            }}
                            tickLabelStyle={{
                                fontSize: 10,
                            }}
                        />
                        <ChartsYAxis
                            label='Requests'
                            position='left'
                            axisId='requests'
                            tickLabelStyle={{ fontSize: 10 }}
                        />
                        <ChartsTooltip />
                    </ResponsiveChartContainer>
                </Wrapper>

                <Box mt={2}>
                    <Typography variant='body2' color='text.secondary'>
                        Total requests in selected range: {totalRequests.toLocaleString()}
                        {usageStats.length > 0 && <> ({usageStats.length} data points)</>}
                    </Typography>
                </Box>
                </>
            }
        </LocalizationProvider>
    );
};
