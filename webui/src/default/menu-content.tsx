/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, PropsWithChildren, useContext, useRef } from 'react';
import { Typography, MenuItem, Link, Button, IconButton, Accordion, AccordionSummary, Avatar, AccordionDetails } from '@mui/material';
import { useLocation, Link as RouteLink  } from 'react-router-dom';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import GitHubIcon from '@mui/icons-material/GitHub';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import ForumIcon from '@mui/icons-material/Forum';
import InfoIcon from '@mui/icons-material/Info';
import PublishIcon from '@mui/icons-material/Publish';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import { UserAvatar } from '../pages/user/avatar';
import { UserSettingsRoutes } from '../pages/user/user-settings-routes';
import { styled, Theme } from '@mui/material/styles';
import { MainContext } from '../context';
import SettingsIcon from '@mui/icons-material/Settings';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import LogoutIcon from '@mui/icons-material/Logout';
import { AdminDashboardRoutes } from '../pages/admin-dashboard/admin-routes';
import { LogoutForm } from '../pages/user/logout';
import { LoginComponent } from './login';

//-------------------- Mobile View --------------------//
// eslint-disable-next-line react-refresh/only-export-components
export const itemIcon = {
    mr: 1,
    width: '16px',
    height: '16px',
};

export const MenuItemText: FunctionComponent<PropsWithChildren> = ({ children }) => {
    return (
        <Typography variant='body2' color='text.primary' sx={{ display: 'flex', alignItems: 'center', textTransform: 'none' }}>
            {children}
        </Typography>
    );
};

export const MobileUserAvatar: FunctionComponent = () => {
    const context = useContext(MainContext);
    const user = context.user;
    const logoutFormRef = useRef<HTMLFormElement>(null);
    if (!user) {
        return null;
    }

    return <Accordion sx={{ border: 0, borderRadius: 0, boxShadow: '0 0', background: 'transparent' }}>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon />}
            aria-controls='user-actions'
            id='user-avatar'
        >
            <MenuItemText>
                <Avatar
                    src={user.avatarUrl}
                    alt={user.loginName}
                    variant='rounded'
                    sx={itemIcon} />
                {user.loginName}
            </MenuItemText>
        </AccordionSummary>
        <AccordionDetails>
            <MenuItem component={Link} href={user.homepage}>
                <MenuItemText>
                    <GitHubIcon sx={itemIcon} />
                    {user.loginName}
                </MenuItemText>
            </MenuItem>
            <MenuItem component={RouteLink} to={UserSettingsRoutes.PROFILE}>
                <MenuItemText>
                    <SettingsIcon sx={itemIcon} />
                    Settings
                </MenuItemText>
            </MenuItem>
            {
                user.role === 'admin'
                    ? <MenuItem component={RouteLink} to={AdminDashboardRoutes.MAIN}>
                        <MenuItemText>
                            <AdminPanelSettingsIcon sx={itemIcon} />
                            Admin Dashboard
                        </MenuItemText>
                    </MenuItem>
                    : null
            }
            <MenuItem onClick={() => logoutFormRef.current?.submit()}>
                <LogoutForm ref={logoutFormRef}>
                    <MenuItemText>
                        <LogoutIcon sx={itemIcon} />
                        Log Out
                    </MenuItemText>
                </LogoutForm>
            </MenuItem>
        </AccordionDetails>
    </Accordion>;
};

export const MobileMenuContent: FunctionComponent = () => {
    const location = useLocation();
    const { user, loginProviders } = useContext(MainContext);

    return <>
        {loginProviders && (
            user ? (
                <MobileUserAvatar />
            ) : (
                <LoginComponent
                    loginProviders={loginProviders}
                    renderButton={(href, onClick) => (
                        <MenuItem component={Link} href={href} onClick={onClick}>
                            <MenuItemText>
                                <AccountBoxIcon sx={itemIcon} />
                                Log In
                            </MenuItemText>
                        </MenuItem>
                    )}
                />
            )
        )}
        {loginProviders && !location.pathname.startsWith(UserSettingsRoutes.ROOT) && (
            <MenuItem component={RouteLink} to='/user-settings/extensions'>
                <MenuItemText>
                    <PublishIcon sx={itemIcon} />
                    Publish Extension
                </MenuItemText>
            </MenuItem>
        )}
        <MenuItem component={Link} href='https://github.com/eclipse/openvsx' target='_blank'>
            <MenuItemText>
                <GitHubIcon sx={itemIcon} />
                Source Code
            </MenuItemText>
        </MenuItem>
        <MenuItem component={Link} href='https://github.com/eclipse/openvsx/wiki'>
            <MenuItemText>
                <MenuBookIcon sx={itemIcon} />
                Documentation
            </MenuItemText>
        </MenuItem>
        <MenuItem component={Link} href='https://join.slack.com/t/openvsxworkinggroup/shared_invite/zt-2y07y1ggy-ct3IfJljjGI6xWUQ9llv6A'>
            <MenuItemText>
                <ForumIcon sx={itemIcon} />
                Slack Workspace
            </MenuItemText>
        </MenuItem>
        <MenuItem component={RouteLink} to='/about'>
            <MenuItemText>
                <InfoIcon sx={itemIcon} />
                About This Service
            </MenuItemText>
        </MenuItem>
    </>;
};

//-------------------- Default View --------------------//

// eslint-disable-next-line react-refresh/only-export-components
export const headerItem = ({ theme }: { theme: Theme }) => ({
    margin: theme.spacing(2.5),
    color: theme.palette.text.primary,
    textDecoration: 'none',
    fontSize: '1.1rem',
    fontFamily: theme.typography.fontFamily,
    fontWeight: theme.typography.fontWeightLight,
    letterSpacing: 1,
    '&:hover': {
        color: theme.palette.secondary.main,
        textDecoration: 'none'
    }
});

// eslint-disable-next-line react-refresh/only-export-components
export const MenuLink = styled(Link)(headerItem);
// eslint-disable-next-line react-refresh/only-export-components
export const MenuRouteLink = styled(RouteLink)(headerItem);

export const DefaultMenuContent: FunctionComponent = () => {
    const { user, loginProviders } = useContext(MainContext);
    return <>
        <MenuLink href='https://github.com/eclipse/openvsx/wiki'>
            Documentation
        </MenuLink>
        <MenuLink href='https://join.slack.com/t/openvsxworkinggroup/shared_invite/zt-2y07y1ggy-ct3IfJljjGI6xWUQ9llv6A'>
            Slack Workspace
        </MenuLink>
        <MenuRouteLink to='/about'>
            About
        </MenuRouteLink>
        {loginProviders && (
            <>
                <Button variant='contained' color='secondary' href='/user-settings/extensions' sx={{ mx: 2.5 }}>
                    Publish
                </Button>
                {
                    user ?
                        <UserAvatar />
                        :
                        <LoginComponent
                            loginProviders={loginProviders}
                            renderButton={(href, onClick) => {
                                if (href) {
                                    return (<IconButton
                                        href={href}
                                        title='Log In'
                                        aria-label='Log In' >
                                        <AccountBoxIcon />
                                    </IconButton>);
                                } else {
                                    return (<IconButton
                                        onClick={onClick}
                                        title='Log In'
                                        aria-label='Log In' >
                                        <AccountBoxIcon />
                                    </IconButton>);
                                }
                            }}
                        />
                }
            </>
        )}
    </>;
};
