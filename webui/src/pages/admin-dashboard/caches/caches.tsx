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
    Alert,
    Box,
    Button,
    Chip,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import { ButtonWithProgress } from '../../../components/button-with-progress';
import type { CacheInfo } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { useCaches, useClearCaches, useRefreshCaches } from './use-caches';

/** A measurement the implementation cannot report, which is not the same as a zero. */
const NOT_MEASURED = '—';

const formatCount = (value?: number): string => (value === undefined ? NOT_MEASURED : value.toLocaleString());

const formatHitRate = (value?: number): string => (value === undefined ? NOT_MEASURED : `${(value * 100).toFixed(1)}%`);

/**
 * Admin dashboard view of the caches: what each holds, how well it is working, and a way to drop it.
 *
 * The reason to have this at all is that a stale cache is invisible from the outside - it answers
 * perfectly well, just with data that is no longer true - and until now clearing one meant restarting
 * the server. The hit rate is what says whether a cache is earning its memory; the entry count is what
 * confirms a clear actually did something.
 */
export const CachesAdmin: FC = () => {
    const { data, isLoading, error } = useCaches();
    const refresh = useRefreshCaches();
    const clear = useClearCaches();
    const [cleared, setCleared] = useState<string | undefined>();

    const clearOne = (cache: CacheInfo) => {
        setCleared(`${cache.name}`);
        clear.mutate({ manager: cache.manager, name: cache.name });
    };

    const clearEverything = () => {
        setCleared(undefined);
        clear.mutate(undefined);
    };

    const caches = data?.caches ?? [];

    return (
        <Box sx={{ p: 3 }}>
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    mb: 3,
                    flexWrap: 'wrap',
                    gap: 2
                }}>
                <Box>
                    <Typography variant='h4' component='h1'>
                        Caches
                    </Typography>
                    <Typography variant='body2' color='text.secondary'>
                        Every cache registered in the application, what it holds and how often it is hit. Clearing one
                        takes effect immediately, on this node.
                    </Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button variant='outlined' startIcon={<RefreshIcon />} onClick={refresh}>
                        Refresh
                    </Button>
                    <ButtonWithProgress
                        working={clear.isPending && cleared === undefined}
                        startIcon={<DeleteSweepIcon />}
                        onClick={clearEverything}>
                        Clear all
                    </ButtonWithProgress>
                </Box>
            </Box>

            {error && <Alert severity='error'>{handleError(error)}</Alert>}
            {clear.error && (
                <Alert severity='error' sx={{ mb: 2 }}>
                    {handleError(clear.error)}
                </Alert>
            )}
            {clear.isSuccess && (
                <Alert severity='success' sx={{ mb: 2 }}>
                    {cleared ? `Cleared ${cleared}.` : 'Cleared every cache.'}
                </Alert>
            )}

            {!error && !isLoading && (
                <TableContainer component={Paper}>
                    <Table size='small' aria-label='Caches'>
                        <TableHead>
                            <TableRow>
                                <TableCell>Cache</TableCell>
                                <TableCell>Manager</TableCell>
                                <TableCell align='right'>Entries</TableCell>
                                <TableCell align='right'>Hits</TableCell>
                                <TableCell align='right'>Misses</TableCell>
                                <TableCell align='right'>Hit rate</TableCell>
                                <TableCell align='right'>Evictions</TableCell>
                                <TableCell align='right'>Actions</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {caches.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={8}>
                                        <Typography variant='body2' color='text.secondary'>
                                            No caches are registered.
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            )}
                            {caches.map(cache => (
                                <TableRow key={`${cache.manager}/${cache.name}`} hover>
                                    <TableCell>
                                        <Typography variant='body2' component='span' sx={{ fontFamily: 'monospace' }}>
                                            {cache.name}
                                        </Typography>{' '}
                                        <Chip label={cache.implementation} size='small' variant='outlined' />
                                    </TableCell>
                                    <TableCell>
                                        <Typography variant='body2' color='text.secondary'>
                                            {cache.manager}
                                        </Typography>
                                    </TableCell>
                                    <MeasurementCell value={formatCount(cache.entries)} />
                                    <MeasurementCell value={formatCount(cache.hits)} />
                                    <MeasurementCell value={formatCount(cache.misses)} />
                                    <MeasurementCell value={formatHitRate(cache.hitRate)} />
                                    <MeasurementCell value={formatCount(cache.evictions)} />
                                    <TableCell align='right'>
                                        <Button
                                            size='small'
                                            onClick={() => clearOne(cache)}
                                            disabled={clear.isPending}
                                            aria-label={`Clear ${cache.name}`}>
                                            Clear
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            )}
        </Box>
    );
};

/** Says why a number is missing, so a dash is not read as a zero. */
const MeasurementCell: FC<{ value: string }> = ({ value }) => {
    if (value !== NOT_MEASURED) {
        return <TableCell align='right'>{value}</TableCell>;
    }

    return (
        <TableCell align='right'>
            <Tooltip title='This cache implementation does not report it'>
                <Typography variant='body2' component='span' color='text.disabled'>
                    {NOT_MEASURED}
                </Typography>
            </Tooltip>
        </TableCell>
    );
};
