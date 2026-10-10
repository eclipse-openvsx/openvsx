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

import { useMemo, useCallback } from 'react';
import { useScanContext } from '../../context/scan-admin';

/**
 * Tab definitions for the Scans Admin page.
 */
export const TAB_DEFINITIONS = [
    { index: 0, name: 'Scans', path: 'scans' },
    { index: 1, name: 'Quarantined', path: 'quarantined' },
    { index: 2, name: 'Auto Rejected', path: 'auto-rejected' },
    { index: 3, name: 'Errors', path: 'errors' },
    { index: 4, name: 'Allowed Files', path: 'allowed-files' },
    { index: 5, name: 'Blocked Files', path: 'blocked-files' }
] as const;

export type TabIndex = 0 | 1 | 2 | 3 | 4 | 5;
export type TabName = (typeof TAB_DEFINITIONS)[number]['name'];

/**
 * Hook for managing tab navigation state and actions.
 */
export const useTabNavigation = () => {
    const { state, actions } = useScanContext();

    const setTab = useCallback(
        (tab: number) => {
            if (tab >= 0 && tab <= 5) {
                actions.setTab(tab);
            }
        },
        [actions]
    );

    const currentTab = useMemo(() => {
        return TAB_DEFINITIONS[state.selectedTab] || TAB_DEFINITIONS[0];
    }, [state.selectedTab]);

    const isScansTab = state.selectedTab === 0;
    const isQuarantinedTab = state.selectedTab === 1;
    const isAutoRejectedTab = state.selectedTab === 2;
    const isErrorsTab = state.selectedTab === 3;
    const isAllowListTab = state.selectedTab === 4;
    const isBlockListTab = state.selectedTab === 5;

    const isScanDataTab = state.selectedTab <= 3;
    const isFileDataTab = state.selectedTab >= 4;

    return useMemo(
        () => ({
            selectedTab: state.selectedTab,
            currentTab,
            tabs: TAB_DEFINITIONS,
            setTab,
            isScansTab,
            isQuarantinedTab,
            isAutoRejectedTab,
            isErrorsTab,
            isAllowListTab,
            isBlockListTab,
            isScanDataTab,
            isFileDataTab
        }),
        [
            state.selectedTab,
            currentTab,
            setTab,
            isScansTab,
            isQuarantinedTab,
            isAutoRejectedTab,
            isErrorsTab,
            isAllowListTab,
            isBlockListTab,
            isScanDataTab,
            isFileDataTab
        ]
    );
};

export type UseTabNavigationReturn = ReturnType<typeof useTabNavigation>;
