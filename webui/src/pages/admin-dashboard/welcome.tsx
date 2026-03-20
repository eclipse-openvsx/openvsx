/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Typography, Grid, Paper } from '@mui/material';
import { styled, Theme } from '@mui/material/styles';
import { Link } from 'react-router-dom';
import { AdminDashboardRoutes } from './admin-routes';

export const Welcome: FunctionComponent = props => {
    return <Grid container direction='column' spacing={2} sx={{ height: '100%' }}>
        <Grid item container direction='column' alignItems='center' justifyContent='flex-end'>
            <Paper elevation={3} sx={{ p: 4 }}>
                <Typography sx={{ mb: 2 }} align='center' variant='h5'>Welcome to the Admin Dashboard!</Typography>
                <Typography align='center'>You can switch pages in the sidepanel menu on the left side.</Typography>
                <Typography align='center'>
                    Choose between administration for
                </Typography>
                <Grid container justifyContent='center' alignItems='center' sx={{ mt: 2 }}>
                    <WelcomeLinkItem route={AdminDashboardRoutes.NAMESPACE_ADMIN} label='Namespaces' description='Manage user roles, create new namespaces' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.EXTENSION_ADMIN} label='Extensions' description='Search for extensions and remove certain versions' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.PUBLISHER_ADMIN} label='Publishers' description='Search for publishers and revoke their contributions' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.SCANS_ADMIN} label='Scans' description='View security scan results and manage quarantined extensions' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.TIERS} label='Tiers' description='Manage rate-limit tiers' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.CUSTOMERS} label='Customers' description='Manage rate-limit customers' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.USAGE_STATS} label='Usage Stats' description='Show usage stats for customers' />
                    <WelcomeLinkItem route={AdminDashboardRoutes.LOGS} label='Logs' description='Browse admin logs' />
                </Grid>
            </Paper>
        </Grid>
    </Grid>;
};

const StyledLink = styled(Link)(({ theme }: { theme: Theme }) => ({
    color: theme.palette.secondary.main,
    textDecoration: 'none',
    '&:hover': {
        textDecoration: 'underline'
    }
}));

const WelcomeLinkItem: FunctionComponent<{ route: string, label: string, description: string }> = props => {
    return <Grid container item xs={8} sx={{ mb: 2 }}>
        <Grid container alignItems='center' item xs={12} md={4}>
            <Typography>
                <StyledLink to={props.route}>
                    {props.label}
                </StyledLink>
            </Typography>
        </Grid>
        <Grid item xs={12} md={8}>
            <Typography variant='body1' style={{ lineHeight: 1.5 }}>{props.description}</Typography>
        </Grid>
    </Grid>;
};