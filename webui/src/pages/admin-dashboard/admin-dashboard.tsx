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
    Breadcrumbs,
    LinkProps,
    Link,
    Toolbar,
    AppBar,
} from '@mui/material';
import { Link as RouterLink, Route, Routes, useNavigate, useLocation } from 'react-router-dom';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import BarChartIcon from '@mui/icons-material/BarChart';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
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
    { path: AdminDashboardRoutes.NAMESPACE_ADMIN, name: 'Namespaces', icon: <AssignmentIndIcon /> },
    { path: AdminDashboardRoutes.EXTENSION_ADMIN, name: 'Extensions', icon: <ExtensionSharpIcon /> },
    { path: AdminDashboardRoutes.PUBLISHER_ADMIN, name: 'Publisher', icon: <PersonIcon /> },
    { path: AdminDashboardRoutes.SCANS_ADMIN, name: 'Scans', icon: <SecurityIcon /> },
    {
        name: 'Rate Limiting',
        icon: <SpeedIcon />,
        children: [
            { path: AdminDashboardRoutes.TIERS, name: 'Tiers', icon: <StarIcon /> },
            { path: AdminDashboardRoutes.CUSTOMERS, name: 'Customers', icon: <PeopleIcon /> },
            { path: AdminDashboardRoutes.USAGE_STATS, name: 'Usage Stats', icon: <BarChartIcon /> },
        ],
    },
    { path: AdminDashboardRoutes.LOGS, name: 'Logs', icon: <HistoryIcon /> },
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

interface LinkRouterProps extends LinkProps {
    to: string;
    replace?: boolean;
}

const LinkRouter = (props: LinkRouterProps) => (
    <Link {...props} component={RouterLink as any} />
);

const BreadcrumbsComponent = () => {
    const { pathname } = useLocation();

    const pathnames = pathname.split("/").filter(Boolean);

    return (
        <Breadcrumbs aria-label='breadcrumb' sx={{ pt: 2, pb: 2, px: 4 }} >
            <LinkRouter underline='hover' color='inherit' to='/'>
                Home
            </LinkRouter>
            {pathnames.map((value, index) => {
                const last = index === pathnames.length - 1;
                const to = `/${pathnames.slice(0, index + 1).join("/")}`;

                return last ? (
                    <Typography color='text.primary' key={to}>
                        {routeNames[to] ?? value}
                    </Typography>
                ) : (
                    <LinkRouter underline='hover' color='inherit' to={to} key={to}>
                        {routeNames[to]}
                    </LinkRouter>
                );
            })}
        </Breadcrumbs>
    );
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
                    <AppBar position='sticky' color='default' enableColorOnDark elevation={0}>
                        <Toolbar>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                                <BreadcrumbsComponent />
                                <IconButton onClick={toMainPage} sx={{ mt: 1, mr: 1 }}>
                                    <HighlightOffIcon />
                                </IconButton>
                            </Box>
                        </Toolbar>
                    </AppBar>
                    <Box
                        sx={{
                            flex: 1,
                            overflowY: 'auto',
                            '&::-webkit-scrollbar': {
                                width: '12px',
                            },
                            '&::-webkit-scrollbar-track': {
                                backgroundColor: 'rgba(0, 0, 0, 0.2)',
                            },
                            '&::-webkit-scrollbar-thumb': {
                                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                                borderRadius: '6px',
                                '&:hover': {
                                    backgroundColor: 'rgba(255, 255, 255, 0.3)',
                                },
                            },
                        }}
                    >
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
                                    <Route path='*' element={<Welcome/>} />
                                </Routes>
                            </Suspense>
                        </Container>
                    </Box>
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