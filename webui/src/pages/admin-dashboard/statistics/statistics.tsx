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

import { FunctionComponent, ReactNode, useContext, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    IconButton,
    Link,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography
} from '@mui/material';
import { BarPlot, ChartsContainer, ChartsTooltip, ChartsXAxis, ChartsYAxis } from '@mui/x-charts';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import DownloadIcon from '@mui/icons-material/Download';
import { DateTime } from 'luxon';
import { MainContext } from '../../../context';
import type { AdminStatistics } from '../../../extension-registry-types';
import { useAdminStatistics } from './use-admin-statistics';

// No explicit locale: every other figure in the web UI is formatted with the viewer's own, from the
// sibling search-index page to the search result count.
const numberFormat = Intl.NumberFormat();
const compactFormat = Intl.NumberFormat(undefined, { notation: 'compact' });

/** A headline figure, laid out so a row of them reads as one band. */
const StatCard: FunctionComponent<{ label: string; value: string; hint?: string }> = ({ label, value, hint }) => (
    <Paper sx={{ p: 2, flex: '1 1 0', minWidth: 150 }}>
        <Typography variant='body2' color='text.secondary'>
            {label}
        </Typography>
        <Typography variant='h5' sx={{ mt: 0.5 }}>
            {value}
        </Typography>
        {hint ? (
            <Typography variant='caption' color='text.secondary'>
                {hint}
            </Typography>
        ) : null}
    </Paper>
);

/** A bar chart over a small labelled series, which is the shape of every breakdown here. */
const BreakdownChart: FunctionComponent<{
    title: string;
    labels: string[];
    values: number[];
    seriesLabel: string;
}> = ({ title, labels, values, seriesLabel }) => {
    if (labels.length === 0) {
        return null;
    }
    return (
        <Paper sx={{ p: 2, flex: '1 1 420px', minWidth: 320 }}>
            <Typography variant='subtitle1' gutterBottom>
                {title}
            </Typography>
            <ChartsContainer
                series={[{ type: 'bar', data: values, label: seriesLabel, color: 'lightgray' }]}
                height={280}
                margin={{ top: 20 }}
                xAxis={[{ id: 'label', data: labels, scaleType: 'band', height: 60 }]}
                yAxis={[
                    {
                        id: 'value',
                        scaleType: 'linear',
                        min: 0,
                        valueFormatter: value => compactFormat.format(value),
                        width: 55
                    }
                ]}>
                <BarPlot />
                <ChartsXAxis axisId='label' />
                <ChartsYAxis axisId='value' />
                <ChartsTooltip />
            </ChartsContainer>
        </Paper>
    );
};

/** A ranked table, for the breakdowns whose labels are identifiers rather than small numbers. */
const RankedTable: FunctionComponent<{
    title: string;
    valueHeader: string;
    rows: { label: string; value: number; href?: string }[];
}> = ({ title, valueHeader, rows }) => {
    if (rows.length === 0) {
        return null;
    }
    return (
        <Paper sx={{ p: 2, flex: '1 1 420px', minWidth: 320 }}>
            <Typography variant='subtitle1' gutterBottom>
                {title}
            </Typography>
            <Table size='small'>
                <TableHead>
                    <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell align='right'>{valueHeader}</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {rows.map(row => (
                        <TableRow key={row.label}>
                            <TableCell>
                                {row.href ? (
                                    <Link href={row.href} underline='hover'>
                                        {row.label}
                                    </Link>
                                ) : (
                                    row.label
                                )}
                            </TableCell>
                            <TableCell align='right'>{numberFormat.format(row.value)}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Paper>
    );
};

export const StatisticsAdmin: FunctionComponent = () => {
    const { service } = useContext(MainContext);
    // UTC throughout, because that is the zone the archival job labels its rows in.
    const currentMonth = useMemo(() => DateTime.utc().startOf('month'), []);
    const [month, setMonth] = useState<DateTime>(currentMonth);

    const isCurrentMonth = month.hasSame(currentMonth, 'month') && month.hasSame(currentMonth, 'year');
    const { data, isFetching, error } = useAdminStatistics(month.year, month.month);

    const heading = month.toFormat('LLLL yyyy');
    const csvUrl = service.admin.getAdminStatisticsCsvUrl(month.year, month.month);

    return (
        <Box>
            <Typography variant='h5' gutterBottom>
                Statistics
            </Typography>

            <Paper sx={{ p: 2, mb: 3 }}>
                <Stack direction='row' spacing={1} alignItems='center' flexWrap='wrap'>
                    <IconButton
                        size='small'
                        aria-label='Previous month'
                        onClick={() => setMonth(month.minus({ months: 1 }))}>
                        <ChevronLeftIcon />
                    </IconButton>
                    <Typography variant='subtitle1' sx={{ minWidth: 160, textAlign: 'center' }}>
                        {heading}
                    </Typography>
                    <IconButton
                        size='small'
                        aria-label='Next month'
                        // There is nothing beyond the month in progress, so stop there.
                        disabled={isCurrentMonth}
                        onClick={() => setMonth(month.plus({ months: 1 }))}>
                        <ChevronRightIcon />
                    </IconButton>
                    <Box sx={{ flexGrow: 1 }} />
                    <Button variant='outlined' size='small' startIcon={<DownloadIcon />} href={csvUrl} disabled={!data}>
                        Download CSV
                    </Button>
                </Stack>
                {isCurrentMonth ? (
                    <Typography variant='caption' color='text.secondary' sx={{ display: 'block', mt: 1 }}>
                        This month is still in progress and is calculated on request. Completed months are archived on
                        the first of the following month.
                    </Typography>
                ) : null}
            </Paper>

            {isFetching && !data ? <CircularProgress /> : null}
            {error && !isFetching && !data ? (
                isNotFoundError(error) ? (
                    <NoStatistics month={heading} />
                ) : (
                    <StatisticsError month={heading} error={error} />
                )
            ) : null}
            {data ? <StatisticsContent statistics={data} /> : null}
        </Box>
    );
};

/**
 * The request layer rejects with an error object carrying the HTTP status (see `server-request.ts`),
 * so a month that was never archived is distinguishable from a request that failed. Only the former
 * is normal.
 */
const isNotFoundError = (error: unknown): boolean => (error as { status?: number })?.status === 404;

/** A request that failed, as opposed to a month that has nothing to show. */
const StatisticsError: FunctionComponent<{ month: string; error: unknown }> = ({ month, error }) => {
    const detail = (error as { message?: string })?.message;
    return (
        <Alert severity='error'>
            The statistics for {month} could not be loaded{detail ? `: ${detail}` : '.'}
        </Alert>
    );
};

/**
 * A month with no data is the normal state for any month that ended before the registry was
 * deployed, or one where the archival job did not run - not an error worth alarming anyone about.
 */
const NoStatistics: FunctionComponent<{ month: string }> = ({ month }) => (
    <Alert severity='info'>
        No statistics were archived for {month}. Statistics are archived on the first of the following month, so months
        before this registry started collecting them have none.
    </Alert>
);

const StatisticsContent: FunctionComponent<{ statistics: AdminStatistics }> = ({ statistics }) => {
    const cards: ReactNode = (
        <Stack direction='row' spacing={2} flexWrap='wrap' useFlexGap sx={{ mb: 3 }}>
            <StatCard label='Extensions' value={numberFormat.format(statistics.extensions)} />
            <StatCard label='Downloads' value={numberFormat.format(statistics.downloads)} hint='this month' />
            <StatCard label='Downloads' value={numberFormat.format(statistics.downloadsTotal)} hint='all time' />
            <StatCard label='Publishers' value={numberFormat.format(statistics.publishers)} />
            <StatCard label='Namespace owners' value={numberFormat.format(statistics.namespaceOwners)} />
            <StatCard
                label='Reviews per extension'
                value={statistics.averageReviewsPerExtension.toFixed(2)}
                hint='average'
            />
        </Stack>
    );

    return (
        <>
            {cards}
            <Stack direction='row' spacing={2} flexWrap='wrap' useFlexGap>
                <BreakdownChart
                    title='Extensions by rating'
                    labels={(statistics.extensionsByRating ?? []).map(e => `${e.rating}★`)}
                    values={(statistics.extensionsByRating ?? []).map(e => e.extensions)}
                    seriesLabel='Extensions'
                />
                <BreakdownChart
                    title='Publishers by extensions published'
                    labels={(statistics.publishersByExtensionsPublished ?? []).map(e => String(e.extensionsPublished))}
                    values={(statistics.publishersByExtensionsPublished ?? []).map(e => e.publishers)}
                    seriesLabel='Publishers'
                />
                <RankedTable
                    title='Most downloaded extensions'
                    valueHeader='Downloads'
                    rows={(statistics.topMostDownloadedExtensions ?? []).map(e => ({
                        label: e.extensionIdentifier,
                        value: e.downloads,
                        href: extensionHref(e.extensionIdentifier)
                    }))}
                />
                <RankedTable
                    title='Most active publishing users'
                    valueHeader='Versions'
                    rows={(statistics.topMostActivePublishingUsers ?? []).map(e => ({
                        label: e.userLoginName,
                        value: e.publishedExtensionVersions
                    }))}
                />
                <RankedTable
                    title='Namespaces by extensions'
                    valueHeader='Extensions'
                    rows={(statistics.topNamespaceExtensions ?? []).map(e => ({
                        label: e.namespace,
                        value: e.extensions,
                        href: `/namespace/${e.namespace}`
                    }))}
                />
                <RankedTable
                    title='Namespaces by extension versions'
                    valueHeader='Versions'
                    rows={(statistics.topNamespaceExtensionVersions ?? []).map(e => ({
                        label: e.namespace,
                        value: e.extensionVersions,
                        href: `/namespace/${e.namespace}`
                    }))}
                />
            </Stack>
        </>
    );
};

/** `namespace.name` is what the server reports; the extension page wants them as path segments. */
function extensionHref(identifier: string): string | undefined {
    const separator = identifier.indexOf('.');
    if (separator <= 0) {
        return undefined;
    }
    return `/extension/${identifier.substring(0, separator)}/${identifier.substring(separator + 1)}`;
}
