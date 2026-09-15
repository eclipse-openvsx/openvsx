/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ComponentType, FunctionComponent, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Avatar, Box, IconButton, Link, Menu, MenuItem, Typography } from '@mui/material';
import { Link as RouteLink } from 'react-router';
import SettingsIcon from '@mui/icons-material/Settings';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import LogoutIcon from '@mui/icons-material/Logout';
import { UserSettingsRoutes } from './user-settings-routes';
import { AdminDashboardRoutes } from '../admin-dashboard/admin-dashboard-routes';
import { MainContext } from '../../context';
import { UserMenuEntryProps } from '../../page-settings';
import { LogoutForm } from './logout';

// Radius, font and min-height come from the MuiMenuItem theme override.
const menuItemSx = {
    py: '0.5rem',
    px: '0.625rem',
    gap: '0.625rem',
    color: 'text.primary',
    display: 'flex',
    alignItems: 'center'
} as const;

const iconSx = { fontSize: '1.0625rem', color: 'text.disabled', flexShrink: 0 };

export const UserAvatar: FunctionComponent = () => {
    const [open, setOpen] = useState(false);
    const close = useCallback(() => setOpen(false), []);
    // Stable identity: a new component type each render would remount contributed entries,
    // refetching whatever they load.
    const MenuEntry = useMemo<ComponentType<UserMenuEntryProps>>(() => {
        const Entry = ({ to, icon: Icon, children }: UserMenuEntryProps) => (
            <MenuItem component={RouteLink} to={to} onClick={close} sx={{ ...menuItemSx, textDecoration: 'none' }}>
                <Icon sx={iconSx} />
                {children}
            </MenuItem>
        );
        Entry.displayName = 'UserMenuEntry';
        return Entry;
    }, [close]);
    const context = useContext(MainContext);
    const { userMenuContent: UserMenuContent } = context.pageSettings.elements;
    const anchorRef = useRef<HTMLButtonElement>(null);
    const logoutFormRef = useRef<HTMLFormElement>(null);

    const user = context.user;
    if (!user) return null;

    const initials = user.loginName.slice(0, 2).toUpperCase();

    return (
        <>
            <IconButton
                ref={anchorRef}
                title={`Logged in as ${user.loginName}`}
                aria-label='User menu'
                onClick={() => setOpen(true)}
                sx={{ p: '0.3125rem' }}>
                <Avatar
                    src={user.avatarUrl}
                    alt={user.loginName}
                    sx={{
                        width: 32,
                        height: 32,
                        bgcolor: 'accentSoft',
                        color: 'secondary.light',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        borderRadius: 1
                    }}>
                    {initials}
                </Avatar>
            </IconButton>
            <Menu
                open={open}
                anchorEl={anchorRef.current}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                onClose={() => setOpen(false)}>
                {/* User header */}
                <Box sx={{ px: '0.875rem', py: '0.875rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <Avatar
                        src={user.avatarUrl}
                        sx={{
                            width: 40,
                            height: 40,
                            bgcolor: 'accentSoft',
                            color: 'secondary.light',
                            fontSize: '0.9375rem',
                            fontWeight: 700,
                            borderRadius: 1,
                            flexShrink: 0
                        }}>
                        {initials}
                    </Avatar>
                    <Box sx={{ minWidth: 0 }}>
                        <Typography
                            sx={{
                                fontSize: '0.6875rem',
                                fontWeight: 700,
                                textTransform: 'uppercase',
                                letterSpacing: '0.07em',
                                color: 'text.disabled',
                                lineHeight: 1.3
                            }}>
                            Logged in as
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.875rem',
                                fontWeight: 700,
                                lineHeight: 1.3,
                                color: 'text.primary',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap'
                            }}>
                            <Link href={user.homepage} target='_blank' onClick={() => setOpen(false)}>
                                {user.loginName}
                            </Link>
                        </Typography>
                    </Box>
                </Box>
                <MenuItem
                    component={RouteLink}
                    to={UserSettingsRoutes.PROFILE}
                    onClick={() => setOpen(false)}
                    sx={{ ...menuItemSx, textDecoration: 'none' }}>
                    <SettingsIcon sx={iconSx} />
                    Settings
                </MenuItem>
                {UserMenuContent ? <UserMenuContent close={close} MenuEntry={MenuEntry} /> : null}
                {user.role === 'admin' && (
                    <MenuItem
                        component={RouteLink}
                        to={AdminDashboardRoutes.MAIN}
                        onClick={() => setOpen(false)}
                        sx={{ ...menuItemSx, textDecoration: 'none' }}>
                        <AdminPanelSettingsIcon sx={iconSx} />
                        Admin Dashboard
                    </MenuItem>
                )}
                <MenuItem onClick={() => logoutFormRef.current?.submit()} sx={menuItemSx}>
                    <LogoutForm ref={logoutFormRef}>
                        <LogoutIcon sx={iconSx} />
                        Log out
                    </LogoutForm>
                </MenuItem>
            </Menu>
        </>
    );
};
