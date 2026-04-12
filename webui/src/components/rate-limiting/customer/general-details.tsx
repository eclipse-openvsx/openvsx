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

import { FC, type ReactNode } from 'react';
import {
    Box,
    Typography,
    Paper,
    type PaperProps,
    Chip,
    Stack,
    Divider,
    Grid,
} from '@mui/material';
import type { Customer } from '../../../extension-registry-types';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export interface GeneralDetailsProps {
    customer: Customer;
    headerAction?: ReactNode;
}

export const GeneralDetails: FC<GeneralDetailsProps> = ({ customer, headerAction }) => {
    const tier = customer.tier;
    return (
        <Paper {...sectionPaperProps}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                <Typography variant='h6'>General Information</Typography>
                {headerAction && <Box sx={{ ml: 'auto' }}>{headerAction}</Box>}
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
                <Grid item xs={12} sm={6} md={4}>
                    <Typography variant='subtitle2' color='text.secondary'>Name</Typography>
                    <Typography variant='body1'>{customer.name}</Typography>
                </Grid>
                <Grid item xs={12} sm={6} md={4}>
                    <Typography variant='subtitle2' color='text.secondary'>State</Typography>
                    <Box sx={{ mt: 0.5 }}>
                        <Chip
                            label={customer.state}
                            size='small'
                            color='secondary'
                        />
                    </Box>
                </Grid>
                {tier ? (
                    <>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Tier</Typography>
                            <Box sx={{ mt: 0.5 }}>
                                <Chip label={tier.name} size='small' />
                            </Box>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Tier Type</Typography>
                            <Typography variant='body2'>{tier.tierType}</Typography>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Capacity</Typography>
                            <Typography variant='body2'>{tier.capacity} requests / {tier.duration}s</Typography>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Refill Strategy</Typography>
                            <Typography variant='body2'>{tier.refillStrategy}</Typography>
                        </Grid>
                        {tier.description && (
                            <Grid item xs={12}>
                                <Typography variant='subtitle2' color='text.secondary'>Tier Description</Typography>
                                <Typography variant='body2'>{tier.description}</Typography>
                            </Grid>
                        )}
                    </>
                ) : (
                    <Grid item xs={12} sm={6} md={4}>
                        <Typography variant='subtitle2' color='text.secondary'>Tier</Typography>
                        <Typography variant='body2' color='text.secondary'>No tier assigned</Typography>
                    </Grid>
                )}
                <Grid item xs={12}>
                    <Typography variant='subtitle2' color='text.secondary'>CIDR Blocks</Typography>
                    {customer.cidrBlocks.length > 0 ? (
                        <Stack direction='row' spacing={0.5} sx={{ mt: 0.5 }} flexWrap='wrap' useFlexGap>
                            {customer.cidrBlocks.map((cidr) => (
                                <Chip key={cidr} label={cidr} size='small' variant='outlined' />
                            ))}
                        </Stack>
                    ) : (
                        <Typography variant='body2' color='text.secondary'>None configured</Typography>
                    )}
                </Grid>
            </Grid>
        </Paper>
    );
};
