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
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import AssessmentIcon from '@mui/icons-material/Assessment';
import BarChartIcon from '@mui/icons-material/BarChart';
import BuildIcon from '@mui/icons-material/Build';
import CategoryIcon from '@mui/icons-material/Category';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import SearchIcon from '@mui/icons-material/Search';
import StorageIcon from '@mui/icons-material/Storage';
import StraightenIcon from '@mui/icons-material/Straighten';
import TroubleshootIcon from '@mui/icons-material/Troubleshoot';
import HistoryIcon from '@mui/icons-material/History';
import PeopleIcon from '@mui/icons-material/People';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import SettingsIcon from '@mui/icons-material/Settings';
import SpeedIcon from '@mui/icons-material/Speed';
import StarIcon from '@mui/icons-material/Star';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import { LoginComponent } from '../../default/login';
import { MainContext } from '../../context';
import { AdminPermission, UserData } from '../../extension-registry-types';
import { hasAnyAdminAccess, hasPermission } from '../../permissions';
import { createRoute } from '../../utils';
import { AdminDashboardRoutes } from './admin-dashboard-routes';
import { AdminSidepanel } from './admin-sidepanel';
import { AdminHeader } from './admin-header';
import { AdminPage, isNavGroup, NavEntry, NavGroup, RouteEntry } from './nav-types';

import { NamespaceAdmin } from './namespace-admin';
import { PublisherAdmin } from './publisher-admin';
import { ScanAdmin } from './scan-admin';
import { Tiers } from './tiers/tiers';
import { SizeOverrides } from './size-overrides/size-overrides';
import { Customers } from './customers/customers';
import { CustomerDetails } from './customers/customer-details';
import { Logs } from './logs/logs';
import { RuntimeSettingsPage } from './settings';
import { Welcome } from './welcome';

const ExtensionAdmin = lazy(() => import('./extension-admin').then(m => ({ default: m.ExtensionAdmin })));
const UsageStatsView = lazy(() => import('./usage-stats/usage-stats').then(m => ({ default: m.UsageStatsView })));
const DataConsistency = lazy(() => import('./consistency/consistency').then(m => ({ default: m.DataConsistency })));
const CachesAdmin = lazy(() => import('./caches/caches').then(m => ({ default: m.CachesAdmin })));
const SearchIndexAdmin = lazy(() => import('./search-index/search-index').then(m => ({ default: m.SearchIndexAdmin })));
const SearchExplainAdmin = lazy(() =>
    import('./search-explain/search-explain').then(m => ({ default: m.SearchExplainAdmin }))
);
const StatisticsAdmin = lazy(() => import('./statistics/statistics').then(m => ({ default: m.StatisticsAdmin })));
const AccessControl = lazy(() => import('./access-control/access-control').then(m => ({ default: m.AccessControl })));

const navConfig: NavEntry[] = [
    {
        name: 'Content',
        icon: <CategoryIcon />,
        children: [
            {
                path: AdminDashboardRoutes.NAMESPACE_ADMIN,
                name: 'Namespaces',
                icon: <AssignmentIndIcon />,
                description: 'Manage user roles and create new namespaces',
                permission: 'manage_namespaces'
            },
            {
                path: AdminDashboardRoutes.EXTENSION_ADMIN,
                name: 'Extensions',
                icon: <ExtensionSharpIcon />,
                description: 'Search for extensions and remove certain versions',
                permission: 'manage_extensions'
            },
            {
                path: AdminDashboardRoutes.PUBLISHER_ADMIN,
                name: 'Publisher',
                icon: <PersonIcon />,
                description: 'Search for publishers and revoke their contributions',
                permission: 'manage_publishers'
            },
            {
                path: AdminDashboardRoutes.SCANS_ADMIN,
                name: 'Scans',
                icon: <SecurityIcon />,
                description: 'View security scan results and manage quarantined extensions',
                permission: 'manage_scans'
            },
            {
                path: AdminDashboardRoutes.SIZE_OVERRIDES,
                name: 'Size overrides',
                icon: <StraightenIcon />,
                description: 'Per-namespace and per-extension upload size limits',
                permission: 'manage_extensions'
            }
        ]
    },
    {
        name: 'Search',
        icon: <SearchIcon />,
        children: [
            {
                path: AdminDashboardRoutes.SEARCH_INDEX,
                name: 'Search Index',
                icon: <ManageSearchIcon />,
                description: 'Inspect the search index and rebuild it',
                permission: 'manage_search_index'
            },
            {
                path: AdminDashboardRoutes.SEARCH_EXPLAIN,
                name: 'Search Explain',
                icon: <TroubleshootIcon />,
                description: "Run a search and see what each result's score is made of",
                permission: 'manage_search_index'
            }
        ]
    },
    {
        name: 'Maintenance',
        icon: <BuildIcon />,
        children: [
            {
                path: AdminDashboardRoutes.CACHES,
                name: 'Caches',
                icon: <StorageIcon />,
                description: 'Inspect the application caches and clear them',
                permission: 'manage_caches'
            },
            {
                path: AdminDashboardRoutes.CONSISTENCY,
                name: 'Data Consistency',
                icon: <FactCheckIcon />,
                description: 'Check the database for known inconsistencies and fix them',
                permission: 'manage_consistency'
            }
        ]
    },
    {
        name: 'Administration',
        icon: <AdminPanelSettingsIcon />,
        children: [
            {
                path: AdminDashboardRoutes.LOGS,
                name: 'Logs',
                icon: <HistoryIcon />,
                description: 'Browse admin activity logs',
                permission: 'view_reports'
            },
            {
                path: AdminDashboardRoutes.SETTINGS,
                name: 'Settings',
                icon: <SettingsIcon />,
                description: 'Manage runtime settings for the registry',
                permission: 'manage_settings'
            },
            {
                path: AdminDashboardRoutes.ACCESS_CONTROL,
                name: 'Access Control',
                icon: <VerifiedUserIcon />,
                description: "Manage a user's role and individually granted permissions",
                adminOnly: true
            }
        ]
    },
    {
        name: 'Rate Limiting',
        icon: <SpeedIcon />,
        children: [
            {
                path: AdminDashboardRoutes.TIERS,
                name: 'Tiers',
                icon: <StarIcon />,
                description: 'Manage rate-limit tiers',
                permission: 'manage_rate_limits'
            },
            {
                path: AdminDashboardRoutes.CUSTOMERS,
                name: 'Customers',
                icon: <PeopleIcon />,
                description: 'Manage rate-limit customers',
                permission: 'manage_rate_limits'
            },
            {
                path: AdminDashboardRoutes.USAGE_STATS,
                name: 'Usage Stats',
                icon: <BarChartIcon />,
                description: 'Show usage stats for customers',
                permission: 'manage_rate_limits'
            }
        ]
    },
    {
        name: 'Analytics',
        icon: <AnalyticsIcon />,
        children: [
            {
                path: AdminDashboardRoutes.STATISTICS,
                name: 'Statistics',
                icon: <AssessmentIcon />,
                description: 'Registry statistics per month, with a CSV export',
                permission: 'view_reports'
            }
        ]
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

/**
 * Whether `entry` belongs in the sidebar/overview for `user` - a contributed page (neither field
 * set) always does; a built-in one needs the permission it declares, or the admin role for one
 * marked {@link RouteEntry.adminOnly}.
 */
function isVisible(entry: RouteEntry, user: UserData | undefined): boolean {
    if (entry.adminOnly) {
        return user?.role === 'admin';
    }
    if (entry.permission) {
        return hasPermission(user, entry.permission);
    }
    return true;
}

/** Drops pages the user has no access to, and any group left with no visible children. */
function filterNavItems(items: NavEntry[], user: UserData | undefined): NavEntry[] {
    return items
        .map(entry =>
            isNavGroup(entry) ? { ...entry, children: entry.children.filter(c => isVisible(c, user)) } : entry
        )
        .filter(entry => (isNavGroup(entry) ? entry.children.length > 0 : isVisible(entry, user)));
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

/**
 * Gates a single admin page by permission, for a user who reached its route directly - the sidebar
 * and overview already leave out what {@link filterNavItems} filtered. Real enforcement is
 * server-side (AdminService#checkPermission); this only avoids rendering a page whose requests
 * would come back 403.
 */
const Guard: FunctionComponent<{ user: UserData | undefined; permission: AdminPermission; children: ReactNode }> = ({
    user,
    permission,
    children
}) => (hasPermission(user, permission) ? <>{children}</> : <Message message='You are not authorized for this page.' />);

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
    const navItems = useMemo(() => filterNavItems(withContributedPages(contributed), user), [contributed, user]);
    const routeNames = useMemo(() => buildRouteNames(navItems), [navItems]);

    const navigate = useNavigate();
    const toMainPage = () => navigate('/');

    let content: ReactNode = null;
    if (hasAnyAdminAccess(user)) {
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
                                    <Route
                                        path='/namespaces'
                                        element={
                                            <Guard user={user} permission='manage_namespaces'>
                                                <NamespaceAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/namespaces/:namespace'
                                        element={
                                            <Guard user={user} permission='manage_namespaces'>
                                                <NamespaceAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/extensions'
                                        element={
                                            <Guard user={user} permission='manage_extensions'>
                                                <ExtensionAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/extensions/:namespace/:extension'
                                        element={
                                            <Guard user={user} permission='manage_extensions'>
                                                <ExtensionAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/publisher'
                                        element={
                                            <Guard user={user} permission='manage_publishers'>
                                                <PublisherAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/publisher/:publisher'
                                        element={
                                            <Guard user={user} permission='manage_publishers'>
                                                <PublisherAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/scans'
                                        element={
                                            <Guard user={user} permission='manage_scans'>
                                                <ScanAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/tiers'
                                        element={
                                            <Guard user={user} permission='manage_rate_limits'>
                                                <Tiers />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/customers'
                                        element={
                                            <Guard user={user} permission='manage_rate_limits'>
                                                <Customers />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/customers/:customer'
                                        element={
                                            <Guard user={user} permission='manage_rate_limits'>
                                                <CustomerDetails />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/statistics'
                                        element={
                                            <Guard user={user} permission='view_reports'>
                                                <StatisticsAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/usage'
                                        element={
                                            <Guard user={user} permission='manage_rate_limits'>
                                                <UsageStatsView />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/usage/:customer'
                                        element={
                                            <Guard user={user} permission='manage_rate_limits'>
                                                <UsageStatsView />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/settings'
                                        element={
                                            <Guard user={user} permission='manage_settings'>
                                                <RuntimeSettingsPage />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/logs'
                                        element={
                                            <Guard user={user} permission='view_reports'>
                                                <Logs />
                                            </Guard>
                                        }
                                    />
                                    {/*
                                        Role and permissions are a privilege-escalation surface, not delegable like
                                        the other permission buckets - checked against role directly, not Guard.
                                    */}
                                    <Route
                                        path='/access-control'
                                        element={
                                            user?.role === 'admin' ? (
                                                <AccessControl />
                                            ) : (
                                                <Message message='You are not authorized for this page.' />
                                            )
                                        }
                                    />
                                    <Route
                                        path='/access-control/:login'
                                        element={
                                            user?.role === 'admin' ? (
                                                <AccessControl />
                                            ) : (
                                                <Message message='You are not authorized for this page.' />
                                            )
                                        }
                                    />
                                    <Route
                                        path='/consistency'
                                        element={
                                            <Guard user={user} permission='manage_consistency'>
                                                <DataConsistency />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/caches'
                                        element={
                                            <Guard user={user} permission='manage_caches'>
                                                <CachesAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/search-index'
                                        element={
                                            <Guard user={user} permission='manage_search_index'>
                                                <SearchIndexAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/search-explain'
                                        element={
                                            <Guard user={user} permission='manage_search_index'>
                                                <SearchExplainAdmin />
                                            </Guard>
                                        }
                                    />
                                    <Route
                                        path='/size-overrides'
                                        element={
                                            <Guard user={user} permission='manage_extensions'>
                                                <SizeOverrides />
                                            </Guard>
                                        }
                                    />
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
