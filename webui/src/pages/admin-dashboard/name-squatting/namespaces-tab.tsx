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

import { FC, useState } from 'react';
import {
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControlLabel,
    MenuItem,
    Paper,
    Skeleton,
    Stack,
    Switch,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Tooltip,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { useSearchParams } from 'react-router';
import type {
    NameSquattingNamespace,
    NameSquattingNamespacePair,
    NameSquattingNamespaceSort
} from '../../../extension-registry-types';
import { Timestamp } from '../../../components/timestamp';
import { formatCompactNumber } from '../../../utils';
import { AllowNamespacesDialog } from './allow-namespaces-dialog';
import { pluralize } from './name-squatting-model';
import { ColumnWidths, LoadError, NamespaceLinks, SimilarityChip, ValuePair } from './name-squatting-parts';
import { GroupCard, GroupTable, Toolbar } from './name-squatting-styles';
import { namespacePairKey, useNameSquattingNamespacePairs } from './use-name-squatting';

const COLUMN_WIDTHS = ['24%', '24%', 'auto', '8rem'];

const SORT_LABELS: Record<NameSquattingNamespaceSort, string> = {
    downloads: 'Most downloaded target first',
    newest: 'Newest look-alike first'
};

/** Kept in the URL, so a link opens the same view; the defaults leave the URL bare. */
const TARGETS_PARAM = 'targets';
const SORT_PARAM = 'sort';

const NamespaceName = styled('span')({
    fontFamily: 'monospace',
    fontWeight: 600
});

const NamespaceCell: FC<{ namespace: NameSquattingNamespace }> = ({ namespace }) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <NamespaceName>{namespace.name}</NamespaceName>
        <Typography variant='caption' color='text.secondary'>
            {formatCompactNumber(namespace.downloads)} downloads · {pluralize(namespace.activeExtensions, 'extension')}
        </Typography>
        <Typography variant='caption' color='text.secondary'>
            {namespace.firstPublished ? (
                <>
                    first published <Timestamp value={namespace.firstPublished} />
                </>
            ) : (
                'Nothing published'
            )}
        </Typography>
        <NamespaceLinks name={namespace.name} />
    </Box>
);

const PairRow: FC<{ pair: NameSquattingNamespacePair; showPopular: boolean; onAllow: () => void }> = ({
    pair,
    showPopular,
    onAllow
}) => (
    <TableRow>
        <TableCell>
            <NamespaceCell namespace={pair.target} />
        </TableCell>
        <TableCell>
            <NamespaceCell namespace={pair.lookalike} />
        </TableCell>
        <TableCell>
            <Stack direction='row' spacing={1} useFlexGap flexWrap='wrap' alignItems='center' sx={{ mb: 0.5 }}>
                <SimilarityChip similarity={pair.similarity} exact={false} />
                <Chip
                    size='small'
                    variant='outlined'
                    label={pair.separatorOnly ? 'Separators only' : pluralize(pair.editDistance, 'edit')}
                />
                {pair.sharedMember && (
                    <Tooltip title='A user is a member of both namespaces, so they are likely the same publisher'>
                        <Chip size='small' color='info' label='Shared member' />
                    </Tooltip>
                )}
                {showPopular && pair.popular && <Chip size='small' color='warning' label='Popular target' />}
            </Stack>
            <ValuePair first={pair.target.name} second={pair.lookalike.name} />
        </TableCell>
        <TableCell align='right'>
            <Button size='small' onClick={onAllow}>
                Allow…
            </Button>
        </TableCell>
    </TableRow>
);

export const NamespacesTab: FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const popularOnly = searchParams.get(TARGETS_PARAM) !== 'all';
    const sort: NameSquattingNamespaceSort = searchParams.get(SORT_PARAM) === 'newest' ? 'newest' : 'downloads';
    const [allowTarget, setAllowTarget] = useState<NameSquattingNamespacePair>();

    const {
        data,
        error,
        isPending,
        isFetching,
        isFetchNextPageError,
        hasNextPage,
        isFetchingNextPage,
        fetchNextPage,
        refetch
    } = useNameSquattingNamespacePairs({ popularOnly, sort });

    const setParam = (key: string, value: string | undefined) =>
        setSearchParams(
            previous => {
                const next = new URLSearchParams(previous);
                if (value === undefined) {
                    next.delete(key);
                } else {
                    next.set(key, value);
                }
                return next;
            },
            { replace: true }
        );

    const renderPairs = () => {
        if (isPending) {
            return error ? null : (
                <Box aria-busy='true' aria-label='Checking namespaces'>
                    {[0, 1, 2].map(index => (
                        <Skeleton key={index} variant='rounded' height='5rem' sx={{ mb: 2 }} />
                    ))}
                </Box>
            );
        }
        if (!data || data.pairs.length === 0) {
            return (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography variant='h6' component='h3'>
                        {popularOnly && (data?.total ?? 0) > 0
                            ? 'No pairs with a popular target'
                            : 'No similar namespace names found'}
                    </Typography>
                    <Typography color='text.secondary'>
                        {popularOnly && (data?.total ?? 0) > 0
                            ? `Turn off "Popular targets only" to see the other ${pluralize(data?.total ?? 0, 'pair')}.`
                            : 'Pairs you have allowed are not shown here; you can review them on the Allowlist tab.'}
                    </Typography>
                </Paper>
            );
        }
        return (
            <GroupCard variant='outlined'>
                <TableContainer>
                    <GroupTable size='small'>
                        <ColumnWidths widths={COLUMN_WIDTHS} />
                        <TableHead>
                            <TableRow>
                                <TableCell>Target (more downloads)</TableCell>
                                <TableCell>Look-alike</TableCell>
                                <TableCell>How the names compare (A = target, B = look-alike)</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {data.pairs.map(pair => (
                                <PairRow
                                    key={namespacePairKey(pair)}
                                    pair={pair}
                                    showPopular={!popularOnly}
                                    onAllow={() => setAllowTarget(pair)}
                                />
                            ))}
                        </TableBody>
                    </GroupTable>
                </TableContainer>
            </GroupCard>
        );
    };

    const shownTotal = popularOnly ? data?.popularTotal : data?.total;

    return (
        <>
            <Toolbar>
                <FormControlLabel
                    control={
                        <Switch
                            checked={popularOnly}
                            onChange={event => setParam(TARGETS_PARAM, event.target.checked ? undefined : 'all')}
                        />
                    }
                    label='Popular targets only'
                />
                <TextField
                    select
                    size='small'
                    label='Sort'
                    value={sort}
                    onChange={event => setParam(SORT_PARAM, event.target.value === 'newest' ? 'newest' : undefined)}
                    sx={{ minWidth: '16rem' }}>
                    {Object.entries(SORT_LABELS).map(([value, label]) => (
                        <MenuItem key={value} value={value}>
                            {label}
                        </MenuItem>
                    ))}
                </TextField>
                <Button
                    variant='outlined'
                    onClick={() => refetch()}
                    disabled={isFetching}
                    startIcon={isFetching && !isFetchingNextPage ? <CircularProgress size={16} /> : undefined}
                    sx={{ ml: 'auto' }}>
                    Check again
                </Button>
            </Toolbar>
            {error && !isFetchNextPageError && <LoadError error={error} onRetry={() => refetch()} />}
            {data && data.pairs.length > 0 && (
                <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                    Showing {data.pairs.length} of {pluralize(shownTotal ?? 0, 'pair')}
                    {popularOnly && ` with a popular target · ${data.total} in all`}
                </Typography>
            )}
            {renderPairs()}
            {error && isFetchNextPageError && <LoadError error={error} onRetry={() => fetchNextPage()} />}
            {hasNextPage && !isFetchNextPageError && (
                <Button
                    fullWidth
                    variant='outlined'
                    onClick={() => fetchNextPage()}
                    disabled={isFetchingNextPage}
                    startIcon={isFetchingNextPage ? <CircularProgress size={16} /> : undefined}>
                    Load more pairs
                </Button>
            )}
            {allowTarget && <AllowNamespacesDialog pair={allowTarget} onClose={() => setAllowTarget(undefined)} />}
        </>
    );
};
