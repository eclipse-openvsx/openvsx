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

import { Box, Skeleton, Typography, alpha, styled, useTheme } from '@mui/material';
import { chartsAxisHighlightClasses } from '@mui/x-charts/ChartsAxisHighlight';
import { lineClasses } from '@mui/x-charts/LineChart';
import { SparkLineChart } from '@mui/x-charts/SparkLineChart';
import { DateTime } from 'luxon';
import { FunctionComponent, useContext, useMemo, useState } from 'react';
import { Eyebrow } from '../../components/page-primitives';
import { MainContext } from '../../context';
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

/**
 * The unit beside the figure. The page header carries a lifetime total of its own, so a bare number
 * here is one of two download counts on the page with nothing at the point of reading to tell them
 * apart - the eyebrow is two lines up.
 */
const Unit = styled(Typography)(({ theme }) => ({
    fontSize: '0.75rem',
    lineHeight: 1.4,
    color: theme.palette.text.secondary,
    whiteSpace: 'nowrap'
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
 * The sidebar slot. Deliberately not a card: it sits flush with the resources group below it, which
 * is styled the same way. Its shape is fixed from first paint, so nothing shifts when the series lands.
 */
const sectionSx = {
    display: 'flex',
    flexDirection: 'column'
} as const;

/**
 * "Weekly downloads" sidebar card: the downloads of the last 7 days, with a sparkline of the weekly
 * totals for the year behind it — one point per week, so the headline is simply its last point.
 * Hovering reads out that week instead. Renders nothing only when download analytics are disabled
 * server-side (the endpoint 404s); otherwise the chart draws immediately, as a flat zero while the
 * series loads and when the extension has no downloads yet, with the figure held as a placeholder.
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

    // A flat zero stands in while the series loads (`isLoading` is the first fetch only, and stays
    // false while the query is disabled) and when there is none, so the chart draws from first paint.
    // Two points, so it is a visible line, not a lone dot.
    const hasData = counts.length > 0;
    const series = hasData ? counts : [0, 0];

    // the last week by default; whole weeks are taken from the end, so a short first week is dropped
    const selected = hovered !== undefined && hovered < series.length ? hovered : series.length - 1;
    // No period without a real week behind it: the zero point is a placeholder, not a dated span.
    let period: string | undefined;
    if (hasData) {
        const first = daily.length - counts.length * WEEK_DAYS + selected * WEEK_DAYS;
        period = formatPeriod(daily[first], daily[first + WEEK_DAYS - 1]);
    }
    // Reserve room for the busiest week, so the headline's width does not track its digit count and
    // resize the sparkline beside it as the pointer moves. Data-derived, so it cannot be a class.
    const busiest = Math.max(...series);
    const reserved = `${busiest.toLocaleString().length}ch`;

    // The chart is an SVG with no text alternative, so without this a screen reader gets the
    // eyebrow, the period and a single number, and nothing at all about the weeks behind them.
    // It names the unit too: per week is exactly what the curve's shape might be read against.
    const weeks = series.length === 1 ? 'the last week' : `the last ${series.length} weeks`;
    const chartLabel = isLoading
        ? 'Weekly downloads, loading'
        : hasData
          ? `Downloads per week over ${weeks}, between ${Math.min(...series).toLocaleString()}` +
            ` and ${busiest.toLocaleString()} per week`
          : 'Downloads per week: no downloads yet';

    return (
        <Box sx={sectionSx} aria-busy={isLoading}>
            <Eyebrow>Weekly downloads</Eyebrow>
            {isLoading ? (
                <Skeleton variant='text' width='55%' sx={{ fontSize: '0.75rem' }} />
            ) : (
                period && <Period>{period}</Period>
            )}
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-end',
                    gap: 1.5,
                    mt: 0.75,
                    borderBottom: `2px solid ${alpha(theme.palette.secondary.main, 0.2)}`
                }}>
                <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
                    {isLoading ? (
                        // Text cannot tween as the chart's data lands, so the figure waits as a placeholder.
                        <Skeleton variant='text' width='4.5rem' sx={{ fontSize: '1.25rem' }} />
                    ) : (
                        <>
                            <DownloadsCount style={{ minWidth: reserved }}>
                                {series[selected].toLocaleString()}
                            </DownloadsCount>
                            <Unit>{series[selected] === 1 ? 'download' : 'downloads'}</Unit>
                        </>
                    )}
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }} role='img' aria-label={chartLabel}>
                    <SparkLineChart
                        data={series}
                        height={CHART_HEIGHT_PX}
                        area
                        // Filled from zero rather than from the quietest week: an area that never
                        // returns to a zero line reads as a running total, which is the one thing
                        // this chart is not. It costs the flattering framing of a low-variance
                        // series, which now looks as flat as it is.
                        baseline={0}
                        margin={{ top: 5, right: 0, bottom: 0, left: 4 }}
                        // Floor the top at 1 so an all-zero series keeps a non-degenerate domain and
                        // its flat line sits on the baseline.
                        yAxis={{ domainLimit: (_, maxValue) => ({ min: 0, max: Math.max(maxValue, 1) }) }}
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
