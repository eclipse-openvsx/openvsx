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

import { FC, ReactNode } from 'react';
import { Alert, Box, Button, Chip, Paper, Table, TableBody, TableCell, TableRow, Typography } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { DateTime } from 'luxon';
import { handleError } from '../../../utils';
import { useAnalyticsStatus, useRefreshAnalyticsStatus } from './use-analytics-status';

const formatTime = (iso?: string): string =>
    iso ? DateTime.fromISO(iso).toLocaleString(DateTime.DATETIME_MED_WITH_SECONDS) : '—';

const Row: FC<{ label: string; children: ReactNode }> = ({ label, children }) => (
    <TableRow>
        <TableCell component='th' scope='row' sx={{ width: '16rem' }}>
            {label}
        </TableCell>
        <TableCell>{children}</TableCell>
    </TableRow>
);

/**
 * Admin dashboard view of download analytics. Analytics is optional: while its database is out the
 * registry keeps serving and only the analytics endpoint and ingestion are affected, which is easy to
 * miss from the outside. This is where an operator sees that it is out, since when, and why.
 */
export const AnalyticsStatusPage: FC = () => {
    const { data, isLoading, error } = useAnalyticsStatus();
    const refresh = useRefreshAnalyticsStatus();

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
                        Download Analytics
                    </Typography>
                    <Typography variant='body2' color='text.secondary'>
                        Whether the analytics database can be reached, and what is waiting for it. The page refreshes
                        itself.
                    </Typography>
                </Box>
                <Button variant='outlined' startIcon={<RefreshIcon />} onClick={refresh}>
                    Refresh
                </Button>
            </Box>

            {error && <Alert severity='error'>{handleError(error)}</Alert>}

            {!error && !isLoading && data && !data.enabled && (
                <Alert severity='info' sx={{ mb: 2 }}>
                    Download analytics is turned off. Set <code>ovsx.analytics.enabled=true</code> and restart to enable
                    it.
                </Alert>
            )}

            {!error && !isLoading && data?.enabled && data.available === false && (
                <Alert severity='warning' sx={{ mb: 2 }}>
                    The analytics database cannot be reached. The registry keeps serving; the download charts show as
                    unavailable, and log files are kept until their events can be stored.
                </Alert>
            )}

            {!error && !isLoading && data && (
                <Paper>
                    <Table size='small' aria-label='Download analytics status'>
                        <TableBody>
                            {data.enabled && (
                                <>
                                    <Row label='Database'>
                                        <Chip
                                            size='small'
                                            label={data.available ? 'Available' : 'Unavailable'}
                                            color={data.available ? 'success' : 'warning'}
                                        />
                                    </Row>
                                    <Row label={data.available ? 'Available since' : 'Unavailable since'}>
                                        {formatTime(data.since)}
                                    </Row>
                                    <Row label='Last checked'>{formatTime(data.lastProbe)}</Row>
                                    {data.lastError && (
                                        <Row label='Last error'>
                                            <Typography variant='body2' sx={{ fontFamily: 'monospace' }}>
                                                {data.lastError}
                                            </Typography>
                                        </Row>
                                    )}
                                </>
                            )}
                            <Row label='Log files waiting for analytics'>{data.pendingFiles.toLocaleString()}</Row>
                            <Row label='Log files that failed ingestion'>{data.failedFiles.toLocaleString()}</Row>
                        </TableBody>
                    </Table>
                </Paper>
            )}
        </Box>
    );
};
