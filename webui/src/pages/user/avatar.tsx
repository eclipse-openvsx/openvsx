/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useRef, useState } from 'react';
import { Avatar, Menu, Typography, MenuItem, Link, Divider, IconButton } from '@mui/material';
import { Link as RouteLink } from 'react-router-dom';
import { UserSettingsRoutes } from './user-settings-routes';
import { AdminDashboardRoutes } from '../admin-dashboard/admin-routes';
import { MainContext } from '../../context';
import { LogoutForm } from './logout';


export const UserAvatar: FunctionComponent = () => {
    const [open, setOpen] = useState<boolean>(false);
    const context = useContext(MainContext);
    const avatarButton = useRef<any>();
    const logoutFormRef = useRef<HTMLFormElement>(null);

    const handleAvatarClick = () => {
        setOpen(!open);
    };

    const handleClose = () => {
        setOpen(false);
    };

    const user = context.user;
    if (!user) {
        return null;
    }
    return <>
        <IconButton
            title={`Logged in as ${user.loginName}`}
            aria-label='User Info'
            onClick={handleAvatarClick}
            ref={(ref: any) => avatarButton.current = ref} >
            <Avatar
                src={user.avatarUrl}
                alt={user.loginName}
                variant='rounded'
                sx={{ width: '30px', height: '30px' }} />
        </IconButton>
        <Menu
            open={open}
            anchorEl={avatarButton.current}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            onClose={handleClose} >
            <MenuItem
                component={Link}
                href={user.homepage}
                sx={{
                    display: 'block',
                    '&:hover': {
                        textDecoration: 'underline'
                    }
                }} >
                <Typography variant='body2' color='text.primary' sx={{ mr: 1 }}>
                    Logged in as
                </Typography>
                <Typography variant='overline' color='text.primary'>
                    {user.loginName}
                </Typography>
            </MenuItem>
            <Divider />
            <MenuItem component={RouteLink} to={UserSettingsRoutes.PROFILE} onClick={handleClose}>
                <Typography variant='button' color='text.primary'>
                    Settings
                </Typography>
            </MenuItem>
            {
                user.role && user.role === 'admin' ?
                    <MenuItem component={RouteLink} to={AdminDashboardRoutes.MAIN} onClick={handleClose}>
                        <Typography variant='button' color='text.primary'>
                            Admin Dashboard
                        </Typography>
                    </MenuItem>
                    :
                    ''
            }
            <MenuItem onClick={() => logoutFormRef.current?.submit()}>
                <LogoutForm ref={logoutFormRef}>
                    <Typography variant='button' sx={{ color: 'primary.dark' }}>
                        Log Out
                    </Typography>
                </LogoutForm>
            </MenuItem>
        </Menu>
    </>;
};