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

import { FunctionComponent, useContext, useMemo, useState } from 'react';
import { Box, Skeleton, Typography, alpha, styled, useTheme } from '@mui/material';
import { SparkLineChart } from '@mui/x-charts/SparkLineChart';
import { lineClasses } from '@mui/x-charts/LineChart';
import { chartsAxisHighlightClasses } from '@mui/x-charts/ChartsAxisHighlight';
import { DateTime } from 'luxon';
import { MainContext } from '../../context';
import { Eyebrow } from '../../components/page-primitives';
import { DownloadSeriesPoint, Extension } from '../../extension-registry-types';
import { useExtensionDownloadSeries } from './use-extension-download-series';

/** Kept modest on purpose: the headline shares its row with the sparkline, which needs the width. */
const DownloadsCount = styled(Typography)(({ theme }) => ({
    fontSize: '1.25rem',
    lineHeight: 1.2,
    fontWeight: 700,
    color: theme.palette.text.primary,
    fontVariantNumeric: 'tabular-nums'
})) as typeof Typography;

const Period = styled(Typography)(({ theme }) => ({
    fontSize: '0.75rem',
    lineHeight: 1.4,
    color: theme.palette.text.secondary,
    fontVariantNumeric: 'tabular-nums'
})) as typeof Typography;

const DAY_AND_MONTH = { month: 'short', day: 'numeric' } as const;

const WEEK_DAYS = 7;

/**
 * Tuned against the headline beside it: the row is bottom-aligned, so its height is the chart's and
 * anything much taller leaves dead space above the number rather than a bigger curve.
 */
const CHART_HEIGHT_PX = 48;

const asDate = (point: DownloadSeriesPoint): DateTime => DateTime.fromISO(point.t, { zone: 'utc' });

/** "Aug 21, 2026" for a single day, or "Aug 15 – Aug 21, 2026" across a week. */
function formatPeriod(from: DownloadSeriesPoint, to: DownloadSeriesPoint): string | undefined {
    const start = asDate(from);
    const end = asDate(to);
    if (!start.isValid || !end.isValid) {
        return undefined;
    }

    const endLabel = end.toLocaleString({ ...DAY_AND_MONTH, year: 'numeric' });
    return start.hasSame(end, 'day') ? endLabel : `${start.toLocaleString(DAY_AND_MONTH)} – ${endLabel}`;
}

/**
 * Folds a daily series into consecutive {@link WEEK_DAYS}-day totals, aligned so the last week ends
 * on the most recent day. Any leading remainder shorter than a whole week is dropped, so every
 * plotted point is a full week sharing no days with its neighbours — a rise or fall on the curve is
 * a real week-on-week change. Oldest first.
 */
function weeklyTotals(daily: number[]): number[] {
    const weeks: number[] = [];
    for (let end = daily.length; end >= WEEK_DAYS; end -= WEEK_DAYS) {
        let total = 0;
        for (let day = end - WEEK_DAYS; day < end; day++) {
            total += daily[day];
        }
        weeks.unshift(total);
    }
    return weeks;
}

/**
 * The sidebar slot, shared by the loaded and loading states so neither shifts. Deliberately not a
 * card: it sits flush with the resources group below it, which is styled the same way.
 */
const sectionSx = {
    display: 'flex',
    flexDirection: 'column',
    flex: { xs: 'none', sm: 'none', md: 1, lg: 1, xl: 'none' },
    mb: { xs: 2, sm: 2, md: 0, lg: 0, xl: 2 }
} as const;

/** Same shape and heights as the loaded section, so the sidebar does not jump when the series lands. */
const LoadingCard: FunctionComponent = () => (
    <Box sx={sectionSx} role='status' aria-label='Loading weekly downloads'>
        <Eyebrow>Weekly downloads</Eyebrow>
        <Skeleton variant='text' width='55%' sx={{ fontSize: '0.75rem' }} />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 1.5, mt: 0.75 }}>
            <Skeleton variant='text' width='4.5rem' sx={{ fontSize: '1.25rem' }} />
            <Skeleton variant='rounded' sx={{ flex: 1, minWidth: 0, height: `${CHART_HEIGHT_PX / 16}rem` }} />
        </Box>
    </Box>
);

/**
 * "Weekly downloads" sidebar card: the downloads of the last 7 days, with a sparkline of the weekly
 * totals for the year behind it — one point per week, so the headline is simply its last point.
 * Hovering reads out that week instead. Renders nothing when download analytics are disabled
 * server-side (the endpoint 404s) or when the extension has no downloads in the year.
 */
export const WeeklyDownloads: FunctionComponent<{ extension: Extension }> = ({ extension }) => {
    const theme = useTheme();
    const { version } = useContext(MainContext);
    const analyticsEnabled = version?.analyticsEnabled ?? false;
    const [hovered, setHovered] = useState<number | undefined>(undefined);

    const { data: points, isLoading } = useExtensionDownloadSeries(extension.namespace, extension.name, {
        enabled: analyticsEnabled
    });

    const daily = useMemo(() => points ?? [], [points]);
    const counts = useMemo(() => weeklyTotals(daily.map(point => point.count)), [daily]);
    if (!analyticsEnabled) {
        return null;
    }
    // `isLoading` is the first fetch only, and stays false while the query is disabled
    if (isLoading) {
        return <LoadingCard />;
    }
    if (counts.length === 0 || !counts.some(count => count > 0)) {
        return null;
    }

    // the last week by default; whole weeks are taken from the end, so a short first week is dropped
    const selected = hovered !== undefined && hovered < counts.length ? hovered : counts.length - 1;
    const first = daily.length - counts.length * WEEK_DAYS + selected * WEEK_DAYS;
    const period = formatPeriod(daily[first], daily[first + WEEK_DAYS - 1]);
    // Reserve room for the busiest week, so the headline's width does not track its digit count and
    // resize the sparkline beside it as the pointer moves. Data-derived, so it cannot be a class.
    const reserved = `${Math.max(...counts).toLocaleString().length}ch`;

    return (
        <Box sx={sectionSx}>
            <Eyebrow>Weekly downloads</Eyebrow>
            {period && <Period>{period}</Period>}
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-end',
                    gap: 1.5,
                    mt: 0.75,
                    borderBottom: `2px solid ${alpha(theme.palette.secondary.main, 0.2)}`
                }}>
                <DownloadsCount style={{ minWidth: reserved }}>{counts[selected].toLocaleString()}</DownloadsCount>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                    <SparkLineChart
                        data={counts}
                        height={CHART_HEIGHT_PX}
                        area
                        // Fill from the bottom of the plot with a little underhang, and trim the
                        // default padding, so the curve uses the box instead of floating in it.
                        baseline='min'
                        margin={{ top: 5, right: 0, bottom: 0, left: 4 }}
                        yAxis={{ domainLimit: (_, maxValue) => ({ min: -maxValue / 6, max: maxValue }) }}
                        clipAreaOffset={{ top: 2, bottom: 2 }}
                        showHighlight
                        // A non-'none' axis highlight is also what enables the axis listener, so
                        // the readout above tracks the pointer anywhere along the curve.
                        axisHighlight={{ x: 'line' }}
                        onHighlightedAxisChange={items => setHovered(items[0]?.dataIndex)}
                        slotProps={{ lineHighlight: { r: 4 } }}
                        color={theme.palette.secondary.main}
                        sx={{
                            [`& .${lineClasses.area}`]: { opacity: 0.2 },
                            [`& .${lineClasses.line}`]: { strokeWidth: 3 },
                            [`& .${chartsAxisHighlightClasses.root}`]: {
                                stroke: theme.palette.secondary.main,
                                strokeDasharray: 'none',
                                strokeWidth: 2
                            }
                        }}
                    />
                </Box>
            </Box>
        </Box>
    );
};
