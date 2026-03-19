/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useState } from 'react';
import {
    Box,
    Container,
    CssBaseline,
    Typography,
    IconButton,
    Breadcrumbs,
    LinkProps,
    Link,
    Toolbar
} from '@mui/material';
import MuiAppBar, { AppBarProps as MuiAppBarProps } from '@mui/material/AppBar';
import { styled } from "@mui/material/styles";
import { Link as RouterLink, Route, Routes, useNavigate, useLocation } from 'react-router-dom';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import BarChartIcon from '@mui/icons-material/BarChart';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import HistoryIcon from '@mui/icons-material/History';
import MenuIcon from "@mui/icons-material/Menu";
import PeopleIcon from '@mui/icons-material/People';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import StarIcon from '@mui/icons-material/Star';
import { CustomerDetails } from './customers/customer-details';
import { Customers } from './customers/customers';
import { DrawerHeader, Sidepanel } from "../../components/sidepanel/sidepanel";
import { ExtensionAdmin } from './extension-admin';
import { LoginComponent } from "../../default/login";
import { Logs } from './logs/logs';
import { MainContext } from '../../context';
import { NamespaceAdmin } from './namespace-admin';
import { NavigationItem } from "../../components/sidepanel/navigation-item";
import { PublisherAdmin } from './publisher-admin';
import { ScanAdmin } from './scan-admin';
import { Tiers } from './tiers/tiers';
import { UsageStatsView } from './usage-stats/usage-stats';
import { Welcome } from './welcome';
import { createRoute } from '../../utils';

export namespace AdminDashboardRoutes {
    export const ROOT = 'admin-dashboard';
    export const MAIN = createRoute([ROOT]);
    export const NAMESPACE_ADMIN = createRoute([ROOT, 'namespaces']);
    export const EXTENSION_ADMIN = createRoute([ROOT, 'extensions']);
    export const PUBLISHER_ADMIN = createRoute([ROOT, 'publisher']);
    export const SCANS_ADMIN = createRoute([ROOT, 'scans']);
    export const TIERS = createRoute([ROOT, 'tiers']);
    export const CUSTOMERS = createRoute([ROOT, 'customers']);
    export const USAGE_STATS = createRoute([ROOT, 'usage']);
    export const LOGS = createRoute([ROOT, 'logs']);
}

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

const routes: { [key: string]: { name: string; component: ReactNode; icon?: ReactNode } } = {};
routes[AdminDashboardRoutes.MAIN] = { name: 'Admin Dashboard', component: <Welcome /> };
routes[AdminDashboardRoutes.NAMESPACE_ADMIN] = { name: 'Namespaces', component: <NamespaceAdmin />, icon: <AssignmentIndIcon /> };
routes[AdminDashboardRoutes.EXTENSION_ADMIN] = { name: 'Extensions', component: <ExtensionAdmin />, icon: <ExtensionSharpIcon /> };
routes[AdminDashboardRoutes.PUBLISHER_ADMIN] = { name: 'Publisher', component: <PublisherAdmin />, icon: <PersonIcon /> };
routes[AdminDashboardRoutes.SCANS_ADMIN] = { name: 'Scans', component: <ScanAdmin />, icon: <SecurityIcon /> };
routes[AdminDashboardRoutes.TIERS] = { name: 'Tiers', component: <Tiers />, icon: <StarIcon /> };
routes[AdminDashboardRoutes.CUSTOMERS] = { name: 'Customers', component: <Customers />, icon: <PeopleIcon /> };
routes[AdminDashboardRoutes.USAGE_STATS] = { name: 'Usage Stats', component: <UsageStatsView />, icon: <BarChartIcon /> };
routes[AdminDashboardRoutes.LOGS] = { name: 'Logs', component: <Logs />, icon: <HistoryIcon /> };

const drawerWidth = 240;

interface AppBarProps extends MuiAppBarProps {
    open?: boolean;
}

const AppBar = styled(MuiAppBar, {
    shouldForwardProp: (prop) => prop !== 'open',
})<AppBarProps>(({ theme }) => ({
    transition: theme.transitions.create(['margin', 'width'], {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    variants: [
        {
            props: ({ open }) => open,
            style: {
                width: `calc(100% - ${drawerWidth}px)`,
                marginLeft: `${drawerWidth}px`,
                transition: theme.transitions.create(['margin', 'width'], {
                    easing: theme.transitions.easing.easeOut,
                    duration: theme.transitions.duration.enteringScreen,
                }),
            },
        },
    ],
}));

interface LinkRouterProps extends LinkProps {
    to: string;
    replace?: boolean;
}

const LinkRouter = (props: LinkRouterProps) => (
    <Link {...props} component={RouterLink as any} />
);

const BreadcrumbsComponent = () => {
    const { pathname } = useLocation();

    const pathnames = pathname.split("/").filter((segment) => segment);

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
                        {routes[to]?.name ?? value}
                    </Typography>
                ) : (
                    <LinkRouter underline='hover' color='inherit' to={to} key={to}>
                        {routes[to]?.name}
                    </LinkRouter>
                );
            })}
        </Breadcrumbs>
    );
};

const Main = styled('main', { shouldForwardProp: (prop) => prop !== 'open' })<{
    open?: boolean;
}>(({ theme }) => ({
    flexGrow: 1,
    padding: theme.spacing(3),
    transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    marginLeft: `-${drawerWidth}px`,
    variants: [
        {
            props: ({ open }) => open,
            style: {
                transition: theme.transitions.create('margin', {
                    easing: theme.transitions.easing.easeOut,
                    duration: theme.transitions.duration.enteringScreen,
                }),
                marginLeft: 0,
            },
        },
    ],
}));

export const AdminDashboard: FunctionComponent<AdminDashboardProps> = props => {
    const { user, loginProviders } = useContext(MainContext);
    const [drawerOpen, setDrawerOpen] = useState(false);

    const navigate = useNavigate();
    const toMainPage = () => navigate('/');

    const [currentPage, setCurrentPage] = useState<string | undefined>(useLocation().pathname);
    const handleOpenRoute = (route: string) => {
        setCurrentPage(route);
    };

    let content: ReactNode = null;
    if (user?.role === 'admin') {
        content = <>
            <Box sx={{ display: 'flex', width: '100%' }}>
                <CssBaseline />
                <AppBar position='fixed' open={drawerOpen}>
                    <Toolbar sx={{ backgroundColor: 'aliceblue' }}>
                        <IconButton
                            aria-label='open drawer'
                            onClick={() => setDrawerOpen(true)}
                            edge='start'
                            sx={[{ mr: 2 }, drawerOpen && { display: 'none' }]}
                        >
                            <MenuIcon />
                        </IconButton>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                            <BreadcrumbsComponent />
                            <IconButton onClick={toMainPage} sx={{ mt: 1, mr: 1 }}>
                                <HighlightOffIcon/>
                            </IconButton>
                        </Box>
                    </Toolbar>
                </AppBar>
                <Sidepanel width={drawerWidth} open={drawerOpen} handleDrawerClose={() => setDrawerOpen(false)} >
                    {Object.keys(routes).map((key, i) => (
                        routes[key].icon &&
                        <NavigationItem key={i} onOpenRoute={handleOpenRoute} active={currentPage?.startsWith(key)}
                                        label={routes[key].name} icon={routes[key].icon} route={key}/>
                    ))}
                </Sidepanel>
                <Main open={drawerOpen} >
                    <DrawerHeader />
                    <Box
                        overflow='auto'
                        flex={1}
                        sx={{
                            overflowY: 'scroll',
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
                        </Container>
                    </Box>
                </Main>
            </Box>
        </>;
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