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
import { Box, Container, CssBaseline, Typography, IconButton } from '@mui/material';
import { createRoute } from '../../utils';
import { Sidepanel } from '../../components/sidepanel/sidepanel';
import { NavigationItem } from '../../components/sidepanel/navigation-item';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import ExtensionSharpIcon from '@mui/icons-material/ExtensionSharp';
import { Route, Routes, useNavigate, useLocation } from 'react-router-dom';
import { NamespaceAdmin } from './namespace-admin';
import { ExtensionAdmin } from './extension-admin';
import { MainContext } from '../../context';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import { Welcome } from './welcome';
import { PublisherAdmin } from './publisher-admin';
import PersonIcon from '@mui/icons-material/Person';
import PeopleIcon from '@mui/icons-material/People';
import { ScanAdmin } from './scan-admin';
import SecurityIcon from '@mui/icons-material/Security';
import StarIcon from '@mui/icons-material/Star';
import BarChartIcon from '@mui/icons-material/BarChart';
import HistoryIcon from '@mui/icons-material/History';
import { Tiers } from './tiers/tiers';
import { Customers } from './customers/customers';
import { CustomerDetails } from './customers/customer-details';
import { UsageStatsView } from './usage-stats/usage-stats';
import { Logs } from './logs/logs';
import { LoginComponent } from "../../default/login";
import AccountBoxIcon from '@mui/icons-material/AccountBox';

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

export const AdminDashboard: FunctionComponent<AdminDashboardProps> = props => {
    const { user, loginProviders } = useContext(MainContext);

    const navigate = useNavigate();
    const toMainPage = () => navigate('/');

    const [currentPage, setCurrentPage] = useState<string | undefined>(useLocation().pathname);
    const handleOpenRoute = (route: string) => setCurrentPage(route);

    let content: ReactNode = null;
    if (user?.role === 'admin') {
        content = <>
            <Sidepanel>
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.NAMESPACE_ADMIN} label='Namespaces' icon={<AssignmentIndIcon />} route={AdminDashboardRoutes.NAMESPACE_ADMIN} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.EXTENSION_ADMIN} label='Extensions' icon={<ExtensionSharpIcon />} route={AdminDashboardRoutes.EXTENSION_ADMIN} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.PUBLISHER_ADMIN} label='Publishers' icon={<PersonIcon />} route={AdminDashboardRoutes.PUBLISHER_ADMIN} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.SCANS_ADMIN} label='Scans' icon={<SecurityIcon />} route={AdminDashboardRoutes.SCANS_ADMIN} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.TIERS} label='Tiers' icon={<StarIcon />} route={AdminDashboardRoutes.TIERS} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.CUSTOMERS} label='Customers' icon={<PeopleIcon />} route={AdminDashboardRoutes.CUSTOMERS} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage?.startsWith(AdminDashboardRoutes.USAGE_STATS)} label='Usage Stats' icon={<BarChartIcon />} route={AdminDashboardRoutes.USAGE_STATS} />
                <NavigationItem onOpenRoute={handleOpenRoute} active={currentPage === AdminDashboardRoutes.LOGS} label='Logs' icon={<HistoryIcon />} route={AdminDashboardRoutes.LOGS} />
            </Sidepanel>
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
                <IconButton onClick={toMainPage} sx={{ float: 'right', mt: 1, mr: 1 }}>
                    <HighlightOffIcon/>
                </IconButton>
                <Container sx={{ pt: 4, pb: 4, px: 3, height: '100%' }} maxWidth='xl'>
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