/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import type { SyntheticEvent } from "react";
import { FunctionComponent, useContext } from 'react';
import { Box, Typography } from '@mui/material';
import { ScanProvider } from '../../context/scan-admin';
import { useTabNavigation, useUrlSync } from '../../hooks/scan-admin';
import {
    TabToolbar,
    TabPanel,
    ScansTabContent,
    QuarantinedTabContent,
    AutoRejectedTabContent,
    AllowListTabContent,
    BlockListTabContent,
    QuarantineDialog,
    FileDialog,
} from '../../components/scan-admin';
import { MainContext } from '../../context';

/**
 * Inner component that consumes the ScanContext.
 * Uses lean tab components that consume context via hooks.
 */
const ScanAdminContent: FunctionComponent = () => {
    const { selectedTab, setTab } = useTabNavigation();

    // Sync state with URL parameters for bookmarkable URLs and browser navigation
    useUrlSync();

    const handleTabChange = (_event: SyntheticEvent, newValue: number) => {
        setTab(newValue);
    };

    return (
        <Box sx={{
            width: '100%',
            maxWidth: 1500,
            mx: 'auto',
            px: 2,
            pb: 3,
        }}>
            <Typography variant='h5' gutterBottom sx={{ mb: 2 }}>
                Extension Scans
            </Typography>
            <Box sx={{ width: '100%' }}>
                <TabToolbar
                    selectedTab={selectedTab}
                    onTabChange={handleTabChange}
                    tabs={['Scans', 'Quarantined', 'Auto Rejected', 'Allowed Files', 'Blocked Files']}
                />

                {/* Tab panels - each tab content component consumes context via hooks */}
                <TabPanel value={selectedTab} index={0}>
                    <ScansTabContent />
                </TabPanel>

                <TabPanel value={selectedTab} index={1}>
                    <QuarantinedTabContent />
                </TabPanel>

                <TabPanel value={selectedTab} index={2}>
                    <AutoRejectedTabContent />
                </TabPanel>

                <TabPanel value={selectedTab} index={3}>
                    <AllowListTabContent />
                </TabPanel>

                <TabPanel value={selectedTab} index={4}>
                    <BlockListTabContent />
                </TabPanel>
            </Box>

            {/* Dialogs - consume context via hooks */}
            <QuarantineDialog />
            <FileDialog />
        </Box>
    );
};

/**
 * Main ScanAdmin component that provides the context.
 * This is the entry point for the scan administration feature.
 */
export const ScanAdmin: FunctionComponent = () => {
    const { service, handleError } = useContext(MainContext);

    return (
        <ScanProvider service={service} handleError={handleError}>
            <ScanAdminContent />
        </ScanProvider>
    );
};

export default ScanAdmin;
