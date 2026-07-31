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

import { FunctionComponent, useContext, useMemo } from 'react';
import { Box, Typography, styled, useTheme } from '@mui/material';
import { SparkLineChart } from '@mui/x-charts/SparkLineChart';
import { MainContext } from '../../context';
import { Eyebrow, cardSurface } from '../../components/page-primitives';
import { Extension } from '../../extension-registry-types';
import { useExtensionDownloadSeries } from './use-extension-download-series';

const DownloadsCard = styled(Box)(({ theme }) => ({
    ...cardSurface(theme),
    padding: '0.75rem 1rem'
}));

const DownloadsCount = styled(Typography)(({ theme }) => ({
    fontSize: '1.75rem',
    lineHeight: 1.1,
    fontWeight: 700,
    color: theme.palette.text.primary,
    fontVariantNumeric: 'tabular-nums'
})) as typeof Typography;

const WINDOW_DAYS = 7;

/** Trailing {@link WINDOW_DAYS}-day rolling sums of a daily series (each point covers that day and
 *  the previous six), so the last value is the downloads of the last week ending today. */
function trailingWeeklySums(daily: number[]): number[] {
    const rolling: number[] = [];
    let windowSum = 0;
    for (let i = 0; i < daily.length; i++) {
        windowSum += daily[i];
        if (i >= WINDOW_DAYS) {
            windowSum -= daily[i - WINDOW_DAYS];
        }
        if (i >= WINDOW_DAYS - 1) {
            rolling.push(windowSum);
        }
    }
    return rolling;
}

/**
 * "Weekly downloads" sidebar card: the downloads of the last 7 days (ending today) plus a trailing
 * 7-day trend sparkline over the last year. Renders nothing when download analytics are disabled
 * server-side (the endpoint 404s) or when the extension has no downloads in the window, so it stays
 * out of the way on registries without data.
 */
export const WeeklyDownloads: FunctionComponent<{ extension: Extension }> = ({ extension }) => {
    const theme = useTheme();
    const { version } = useContext(MainContext);
    const analyticsEnabled = version?.analyticsEnabled ?? false;

    const { data: points } = useExtensionDownloadSeries(extension.namespace, extension.name, {
        enabled: analyticsEnabled
    });

    const counts = useMemo(() => trailingWeeklySums(points?.map(point => point.count) ?? []), [points]);
    const hasDownloads = counts.some(count => count > 0);
    if (!analyticsEnabled || counts.length === 0 || !hasDownloads) {
        return null;
    }

    const latestWeek = counts[counts.length - 1];

    return (
        <Box sx={{ flex: { md: 1, lg: 1, xl: 'none' }, mb: { xs: 2, sm: 2, md: 0, lg: 0, xl: 2 } }}>
            <DownloadsCard>
                <Eyebrow>Weekly downloads</Eyebrow>
                <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1.5, mt: 0.75 }}>
                    <DownloadsCount>{latestWeek.toLocaleString()}</DownloadsCount>
                    <Box sx={{ flex: 1, minWidth: 0, height: '2.75rem' }}>
                        <SparkLineChart
                            data={counts}
                            height={44}
                            area
                            curve='linear'
                            showTooltip
                            showHighlight
                            color={theme.palette.secondary.main}
                            valueFormatter={value => (value === null ? '' : `${value.toLocaleString()} downloads`)}
                        />
                    </Box>
                </Box>
            </DownloadsCard>
        </Box>
    );
};
