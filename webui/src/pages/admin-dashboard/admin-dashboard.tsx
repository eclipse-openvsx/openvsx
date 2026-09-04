/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useMemo, lazy, Suspense } from 'react';
import { Box, Container, CssBaseline, Typography, IconButton } from '@mui/material';
import { styled } from '@mui/material/styles';
import { Route, Routes, useNavigate } from 'react-router';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import AssessmentIcon from '@mui/icons-material/Assessment';
import BarChartIcon from '@mui/icons-material/BarChart';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import HistoryIcon from '@mui/icons-material/History';
import PeopleIcon from '@mui/icons-material/People';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import SettingsIcon from '@mui/icons-material/Settings';
import SpeedIcon from '@mui/icons-material/Speed';
import StarIcon from '@mui/icons-material/Star';
import { LoginComponent } from '../../default/login';
import { MainContext } from '../../context';
import { createRoute } from '../../utils';
import { AdminDashboardRoutes } from './admin-dashboard-routes';
import { AdminSidepanel } from './admin-sidepanel';
import { AdminHeader } from './admin-header';
import { AdminPage, isNavGroup, NavEntry, NavGroup, RouteEntry } from './nav-types';

import { NamespaceAdmin } from './namespace-admin';
import { PublisherAdmin } from './publisher-admin';
import { ScanAdmin } from './scan-admin';
import { Tiers } from './tiers/tiers';
import { Customers } from './customers/customers';
import { CustomerDetails } from './customers/customer-details';
import { Logs } from './logs/logs';
import { RuntimeSettingsPage } from './settings';
import { Welcome } from './welcome';

const ExtensionAdmin = lazy(() => import('./extension-admin').then(m => ({ default: m.ExtensionAdmin })));
const UsageStatsView = lazy(() => import('./usage-stats/usage-stats').then(m => ({ default: m.UsageStatsView })));
const DataConsistency = lazy(() => import('./consistency/consistency').then(m => ({ default: m.DataConsistency })));
const SearchIndexAdmin = lazy(() => import('./search-index/search-index').then(m => ({ default: m.SearchIndexAdmin })));
const StatisticsAdmin = lazy(() => import('./statistics/statistics').then(m => ({ default: m.StatisticsAdmin })));

const navConfig: NavEntry[] = [
    {
        path: AdminDashboardRoutes.NAMESPACE_ADMIN,
        name: 'Namespaces',
        icon: <AssignmentIndIcon />,
        description: 'Manage user roles and create new namespaces'
    },
    {
        path: AdminDashboardRoutes.EXTENSION_ADMIN,
        name: 'Extensions',
        icon: <ExtensionSharpIcon />,
        description: 'Search for extensions and remove certain versions'
    },
    {
        path: AdminDashboardRoutes.PUBLISHER_ADMIN,
        name: 'Publisher',
        icon: <PersonIcon />,
        description: 'Search for publishers, update roles, and revoke their contributions'
    },
    {
        path: AdminDashboardRoutes.SCANS_ADMIN,
        name: 'Scans',
        icon: <SecurityIcon />,
        description: 'View security scan results and manage quarantined extensions'
    },
    {
        name: 'Rate Limiting',
        icon: <SpeedIcon />,
        children: [
            {
                path: AdminDashboardRoutes.TIERS,
                name: 'Tiers',
                icon: <StarIcon />,
                description: 'Manage rate-limit tiers'
            },
            {
                path: AdminDashboardRoutes.CUSTOMERS,
                name: 'Customers',
                icon: <PeopleIcon />,
                description: 'Manage rate-limit customers'
            },
            {
                path: AdminDashboardRoutes.USAGE_STATS,
                name: 'Usage Stats',
                icon: <BarChartIcon />,
                description: 'Show usage stats for customers'
            }
        ]
    },
    {
        path: AdminDashboardRoutes.SETTINGS,
        name: 'Settings',
        icon: <SettingsIcon />,
        description: 'Manage runtime settings for the registry'
    },
    { path: AdminDashboardRoutes.LOGS, name: 'Logs', icon: <HistoryIcon />, description: 'Browse admin activity logs' },
    {
        path: AdminDashboardRoutes.CONSISTENCY,
        name: 'Data Consistency',
        icon: <FactCheckIcon />,
        description: 'Check the database for known inconsistencies and fix them'
    },
    {
        path: AdminDashboardRoutes.SEARCH_INDEX,
        name: 'Search Index',
        icon: <ManageSearchIcon />,
        description: 'Inspect the search index and rebuild it'
    },
    {
        path: AdminDashboardRoutes.STATISTICS,
        name: 'Statistics',
        icon: <AssessmentIcon />,
        description: 'Registry statistics per month, with a CSV export'
    }
];

/** First path segment of every built-in page, so a contributed page cannot shadow one. */
const builtInSegments = new Set(
    navConfig
        .flatMap(entry => (isNavGroup(entry) ? entry.children : [entry]))
        .map(entry => entry.path.slice(AdminDashboardRoutes.MAIN.length + 1).split('/')[0])
);

/**
 * Contributed paths come from a consumer, so they are normalized before anything derives a route or a
 * nav link from them. A leading slash would leave the shadowing check below inspecting an empty first
 * segment - so '/customers' would pass it and sit in the nav next to the built-in page it names - and
 * createRoute would join it into '/admin-dashboard//customers'.
 */
const normalizePagePath = (path: string) => path.replace(/^\/+/, '').replace(/\/+$/, '');

const toRouteEntry = (page: AdminPage): RouteEntry => ({
    path: createRoute([AdminDashboardRoutes.ROOT, page.path]),
    name: page.name,
    icon: page.icon,
    description: page.description
});

/** Appends contributed pages, merging each category into a group of that name if one already exists. */
function withContributedPages(pages: AdminPage[]): NavEntry[] {
    const entries = navConfig.map(entry => (isNavGroup(entry) ? { ...entry, children: [...entry.children] } : entry));
    for (const page of pages) {
        const entry = toRouteEntry(page);
        if (!page.category) {
            entries.push(entry);
            continue;
        }
        const group = entries.find((e): e is NavGroup => isNavGroup(e) && e.name === page.category!.name);
        if (group) {
            group.children.push(entry);
        } else {
            entries.push({ name: page.category.name, icon: page.category.icon, children: [entry] });
        }
    }
    return entries;
}

function buildRouteNames(items: NavEntry[]): { [key: string]: string } {
    return {
        [AdminDashboardRoutes.MAIN]: 'Admin Dashboard',
        ...items.reduce<{ [key: string]: string }>((acc, entry) => {
            if (isNavGroup(entry)) {
                entry.children.forEach(child => {
                    acc[child.path] = child.name;
                });
            } else {
                acc[entry.path] = entry.name;
            }
            return acc;
        }, {})
    };
}

const ScrollableContent = styled(Box)(({ theme }) => ({
    flex: 1,
    overflowY: 'auto',
    '&::-webkit-scrollbar': {
        width: '12px'
    },
    '&::-webkit-scrollbar-track': {
        backgroundColor: theme.palette.action.hover
    },
    '&::-webkit-scrollbar-thumb': {
        backgroundColor: theme.palette.action.selected,
        borderRadius: '6px',
        '&:hover': {
            backgroundColor: theme.palette.action.focus
        }
    }
}));

const Message: FunctionComponent<{ message: string }> = ({ message }) => {
    return (
        <Box
            sx={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                width: '100%'
            }}>
            <Typography variant='h6'>{message}</Typography>
        </Box>
    );
};

export const AdminDashboard: FunctionComponent<AdminDashboardProps> = props => {
    const { user, loginProviders, pageSettings } = useContext(MainContext);

    const adminPages = pageSettings.elements.adminPages;
    const contributed = useMemo(
        () =>
            (adminPages ?? [])
                .map(page => ({ ...page, path: normalizePagePath(page.path) }))
                .filter(page => page.path.length > 0 && !builtInSegments.has(page.path.split('/')[0])),
        [adminPages]
    );
    const navItems = useMemo(() => withContributedPages(contributed), [contributed]);
    const routeNames = useMemo(() => buildRouteNames(navItems), [navItems]);

    const navigate = useNavigate();
    const toMainPage = () => navigate('/');

    let content: ReactNode = null;
    if (user?.role === 'admin') {
        content = (
            <Box sx={{ display: 'flex', width: '100%', height: '100%' }}>
                <CssBaseline />
                <AdminSidepanel items={navItems} />
                <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
                    <AdminHeader routeNames={routeNames} onClose={toMainPage} />
                    <ScrollableContent>
                        <Container sx={{ pt: 3, pb: 4, px: 3 }} maxWidth={false}>
                            <Suspense fallback={null}>
                                <Routes>
                                    <Route path='/namespaces' element={<NamespaceAdmin />} />
                                    <Route path='/namespaces/:namespace' element={<NamespaceAdmin />} />
                                    <Route path='/extensions' element={<ExtensionAdmin />} />
                                    <Route path='/extensions/:namespace/:extension' element={<ExtensionAdmin />} />
                                    <Route path='/publisher' element={<PublisherAdmin />} />
                                    <Route path='/publisher/:publisher' element={<PublisherAdmin />} />
                                    <Route path='/scans' element={<ScanAdmin />} />
                                    <Route path='/tiers' element={<Tiers />} />
                                    <Route path='/customers' element={<Customers />} />
                                    <Route path='/customers/:customer' element={<CustomerDetails />} />
                                    <Route path='/statistics' element={<StatisticsAdmin />} />
                                    <Route path='/usage' element={<UsageStatsView />} />
                                    <Route path='/usage/:customer' element={<UsageStatsView />} />
                                    <Route path='/settings' element={<RuntimeSettingsPage />} />
                                    <Route path='/logs' element={<Logs />} />
                                    <Route path='/consistency' element={<DataConsistency />} />
                                    <Route path='/search-index' element={<SearchIndexAdmin />} />
                                    {/* Splat so a contributed page can render nested routes; it also matches the bare path. */}
                                    {contributed.map(page => (
                                        <Route key={page.path} path={`${page.path}/*`} element={page.element} />
                                    ))}
                                    <Route path='*' element={<Welcome items={navItems} />} />
                                </Routes>
                            </Suspense>
                        </Container>
                    </ScrollableContent>
                </Box>
            </Box>
        );
    } else if (user) {
        content = <Message message='You are not authorized as administrator.' />;
    } else if (!props.userLoading && loginProviders) {
        content = (
            <Box display='flex' alignItems='center'>
                <Message message='You are not logged in.' />
                <Box height='fit-content' alignItems='center' display='flex'>
                    <LoginComponent
                        loginProviders={loginProviders}
                        renderButton={(href, onClick) => {
                            if (href) {
                                return (
                                    <IconButton href={href} title='Log In' aria-label='Log In'>
                                        <AccountBoxIcon />
                                    </IconButton>
                                );
                            } else {
                                return (
                                    <IconButton onClick={onClick} title='Log In' aria-label='Log In'>
                                        <AccountBoxIcon />
                                    </IconButton>
                                );
                            }
                        }}
                    />
                </Box>
            </Box>
        );
    }

    return (
        <>
            <CssBaseline />
            <Box display='flex' height='100vh' justifyContent='center'>
                {content}
            </Box>
        </>
    );
};

export interface AdminDashboardProps {
    userLoading: boolean;
}
