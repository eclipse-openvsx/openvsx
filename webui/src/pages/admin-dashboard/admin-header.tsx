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
import { AppBar, Breadcrumbs, IconButton, Link, Toolbar, Typography } from '@mui/material';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import { Link as RouterLink, useLocation } from 'react-router-dom';

export interface AdminHeaderProps {
    routeNames: Record<string, string>;
    onClose: () => void;
}

const BreadcrumbsNav: FunctionComponent<{ routeNames: Record<string, string> }> = ({ routeNames }) => {
    const { pathname } = useLocation();
    const segments = pathname.split('/').filter(Boolean);

    return (
        <Breadcrumbs aria-label='breadcrumb' sx={{ pt: 2, pb: 2, px: 4 }}>
            <Link component={RouterLink} to='/' underline='hover' color='inherit'>
                Home
            </Link>
            {segments.map((value, index) => {
                const to = `/${segments.slice(0, index + 1).join('/')}`;
                const label = routeNames[to] ?? value;
                const isLast = index === segments.length - 1;

                return isLast ? (
                    <Typography color='text.primary' key={to}>{label}</Typography>
                ) : (
                    <Link component={RouterLink} to={to} underline='hover' color='inherit' key={to}>
                        {label}
                    </Link>
                );
            })}
        </Breadcrumbs>
    );
};

export const AdminHeader: FunctionComponent<AdminHeaderProps> = ({ routeNames, onClose }) => (
    <AppBar position='sticky' color='default' enableColorOnDark elevation={0}>
        <Toolbar sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <BreadcrumbsNav routeNames={routeNames} />
            <IconButton onClick={onClose} aria-label='close admin dashboard' sx={{ mt: 1, mr: 1 }}>
                <HighlightOffIcon />
            </IconButton>
        </Toolbar>
    </AppBar>
);
