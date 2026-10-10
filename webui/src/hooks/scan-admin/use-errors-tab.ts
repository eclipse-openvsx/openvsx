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

import { useCallback, useMemo } from 'react';
import { useScanContext } from '../../context/scan-admin';
import { useScanFilters } from './use-scan-filters';
import { usePagination } from './use-pagination';
import { useSearch } from './use-search';

/**
 * Hook for the Errors tab (tab index 3).
 * Provides errored scans together with selection and the allow action, for scans
 * that cannot pass because of a scanner failure that retrying does not resolve.
 */
export const useErrorsTab = () => {
    const { state, actions } = useScanContext();
    const { globalFilters, quarantineFilters } = useScanFilters();
    const pagination = usePagination();
    const search = useSearch();

    const scans = state.scans;

    // An errored scan can be allowed only once.
    const selectableScans = useMemo(() => scans.filter(scan => !scan.adminDecision?.decision), [scans]);

    const selectedCount = useMemo(
        () => Object.values(state.quarantinedChecked).filter(Boolean).length,
        [state.quarantinedChecked]
    );

    const toggleCheck = useCallback(
        (id: string, checked: boolean) => actions.toggleQuarantinedCheck(id, checked),
        [actions]
    );
    const selectAll = useCallback(() => actions.selectAllQuarantined(selectableScans), [actions, selectableScans]);
    const deselectAll = useCallback(() => actions.deselectAllQuarantined(), [actions]);

    const isAllSelected = useMemo(
        () => selectableScans.length > 0 && selectableScans.every(scan => state.quarantinedChecked[scan.id]),
        [selectableScans, state.quarantinedChecked]
    );

    return useMemo(
        () => ({
            tabIndex: 3,
            tabName: 'Errors',
            scans,
            isLoading: state.isLoadingScans,
            lastRefreshed: state.lastRefreshed,
            autoRefresh: state.autoRefresh,
            onAutoRefreshChange: actions.setAutoRefresh,
            totalCount: state.scanCounts?.ERROR ?? 0,
            search,
            globalFilters,
            quarantineFilters,
            pagination,
            selectedCount,
            checked: state.quarantinedChecked,
            toggleCheck,
            selectAll,
            deselectAll,
            isAllSelected,
            openAllowDialog: actions.openAllowDialog
        }),
        [
            scans,
            state.isLoadingScans,
            state.lastRefreshed,
            state.autoRefresh,
            state.scanCounts,
            state.quarantinedChecked,
            actions,
            search,
            globalFilters,
            quarantineFilters,
            pagination,
            selectedCount,
            toggleCheck,
            selectAll,
            deselectAll,
            isAllSelected
        ]
    );
};

export type UseErrorsTabReturn = ReturnType<typeof useErrorsTab>;
