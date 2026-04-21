/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, lazy, Suspense } from 'react';
import {
    Box,
    Container,
    CssBaseline,
    Typography,
    IconButton,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { Route, Routes, useNavigate } from 'react-router-dom';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import BarChartIcon from '@mui/icons-material/BarChart';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import HistoryIcon from '@mui/icons-material/History';
import PeopleIcon from '@mui/icons-material/People';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import SpeedIcon from '@mui/icons-material/Speed';
import StarIcon from '@mui/icons-material/Star';
import { LoginComponent } from "../../default/login";
import { MainContext } from '../../context';
import { AdminDashboardRoutes } from './admin-dashboard-routes';
import { AdminSidepanel } from './admin-sidepanel';
import { AdminHeader } from './admin-header';
import { isNavGroup, NavEntry } from './nav-types';

import { NamespaceAdmin } from './namespace-admin';
import { PublisherAdmin } from './publisher-admin';
import { ScanAdmin } from './scan-admin';
import { Tiers } from './tiers/tiers';
import { Customers } from './customers/customers';
import { CustomerDetails } from './customers/customer-details';
import { Logs } from './logs/logs';
import { Welcome } from './welcome';

const ExtensionAdmin = lazy(() => import('./extension-admin').then(m => ({ default: m.ExtensionAdmin })));
const UsageStatsView = lazy(() => import('./usage-stats/usage-stats').then(m => ({ default: m.UsageStatsView })));

const navConfig: NavEntry[] = [
    { path: AdminDashboardRoutes.NAMESPACE_ADMIN, name: 'Namespaces', icon: <AssignmentIndIcon />, description: 'Manage user roles and create new namespaces' },
    { path: AdminDashboardRoutes.EXTENSION_ADMIN, name: 'Extensions', icon: <ExtensionSharpIcon />, description: 'Search for extensions and remove certain versions' },
    { path: AdminDashboardRoutes.PUBLISHER_ADMIN, name: 'Publisher', icon: <PersonIcon />, description: 'Search for publishers and revoke their contributions' },
    { path: AdminDashboardRoutes.SCANS_ADMIN, name: 'Scans', icon: <SecurityIcon />, description: 'View security scan results and manage quarantined extensions' },
    {
        name: 'Rate Limiting',
        icon: <SpeedIcon />,
        children: [
            { path: AdminDashboardRoutes.TIERS, name: 'Tiers', icon: <StarIcon />, description: 'Manage rate-limit tiers' },
            { path: AdminDashboardRoutes.CUSTOMERS, name: 'Customers', icon: <PeopleIcon />, description: 'Manage rate-limit customers' },
            { path: AdminDashboardRoutes.USAGE_STATS, name: 'Usage Stats', icon: <BarChartIcon />, description: 'Show usage stats for customers' },
        ],
    },
    { path: AdminDashboardRoutes.LOGS, name: 'Logs', icon: <HistoryIcon />, description: 'Browse admin activity logs' },
];

const routeNames: { [key: string]: string } = {
    [AdminDashboardRoutes.MAIN]: 'Admin Dashboard',
    ...navConfig.reduce<{ [key: string]: string }>((acc, entry) => {
        if (isNavGroup(entry)) {
            entry.children.forEach(child => {
                acc[child.path] = child.name;
            });
        } else {
            acc[entry.path] = entry.name;
        }
        return acc;
    }, {}),
};

const ScrollableContent = styled(Box)(({ theme }) => ({
    flex: 1,
    overflowY: 'auto',
    '&::-webkit-scrollbar': {
        width: '12px',
    },
    '&::-webkit-scrollbar-track': {
        backgroundColor: theme.palette.action.hover,
    },
    '&::-webkit-scrollbar-thumb': {
        backgroundColor: theme.palette.action.selected,
        borderRadius: '6px',
        '&:hover': {
            backgroundColor: theme.palette.action.focus,
        },
    },
}));

const Message: FunctionComponent<{message: string}> = ({ message }) => {
    return (<Box sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        width: '100%'
    }}>
        <Typography variant='h6'>{message}</Typography>
    </Box>);
};

export const AdminDashboard: FunctionComponent<AdminDashboardProps> = props => {
    const { user, loginProviders } = useContext(MainContext);

    const navigate = useNavigate();
    const toMainPage = () => navigate('/');

    let content: ReactNode = null;
    if (user?.role === 'admin') {
        content =
            <Box sx={{ display: 'flex', width: '100%', height: '100%' }}>
                <CssBaseline />
                <AdminSidepanel items={navConfig} />
                <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
                    <AdminHeader routeNames={routeNames} onClose={toMainPage} />
                    <ScrollableContent>
                        <Container sx={{ pt: 2, pb: 4, px: 3 }} maxWidth='xl'>
                            <Suspense fallback={null}>
                                <Routes>
                                    <Route path='/namespaces' element={<NamespaceAdmin/>} />
                                    <Route path='/extensions' element={<ExtensionAdmin/>} />
                                    <Route path='/extensions/:namespace/:extension' element={<ExtensionAdmin/>} />
                                    <Route path='/publisher' element={<PublisherAdmin/>} />
                                    <Route path='/publisher/:publisher' element={<PublisherAdmin/>} />
                                    <Route path='/scans' element={<ScanAdmin/>} />
                                    <Route path='/tiers' element={<Tiers/>} />
                                    <Route path='/customers' element={<Customers/>} />
                                    <Route path='/customers/:customer' element={<CustomerDetails/>} />
                                    <Route path='/usage' element={<UsageStatsView/>} />
                                    <Route path='/usage/:customer' element={<UsageStatsView/>} />
                                    <Route path='/logs' element={<Logs/>} />
                                    <Route path='*' element={<Welcome items={navConfig} />} />
                                </Routes>
                            </Suspense>
                        </Container>
                    </ScrollableContent>
                </Box>
            </Box>;
    } else if (user) {
        content = <Message message='You are not authorized as administrator.'/>;
    } else if (!props.userLoading && loginProviders) {

        content = <Box display='flex' alignItems='center'>
            <Message message='You are not logged in.'/>
            <Box height='fit-content' alignItems='center' display='flex'>
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
            </Box>
        </Box>;
    }

    return <>
        <CssBaseline />
        <Box display='flex' height='100vh' justifyContent='center'>{content}</Box>
    </>;
};

export interface AdminDashboardProps {
    userLoading: boolean;
}