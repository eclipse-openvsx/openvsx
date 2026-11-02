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
import { ScanResult, useScanContext } from '../../context/scan-admin';
import { useScanFilters } from './use-scan-filters';
import { usePagination } from './use-pagination';
import { useSearch } from './use-search';

/**
 * A quarantined scan needs review while it has no admin decision and an enforced threat or validation
 * failure holds it; a check that fails at activation quarantines a version without any threat.
 */
export const needsQuarantineReview = (scan: ScanResult): boolean =>
    !scan.adminDecision?.decision &&
    (scan.threats.some(t => t.enforcedFlag) || scan.validationFailures.some(f => f.enforcedFlag));

/**
 * Hook for the Quarantined tab (tab index 1).
 * Provides quarantined scan data, selection management, and bulk actions.
 */
export const useQuarantinedTab = () => {
    const { state, actions, derived } = useScanContext();
    const { globalFilters, quarantineFilters } = useScanFilters();
    const pagination = usePagination();
    const search = useSearch();

    // Scans are already filtered server-side by threatScannerName parameter
    const quarantinedScans = state.scans;

    const selectableScans = useMemo(() => quarantinedScans.filter(needsQuarantineReview), [quarantinedScans]);

    // Selection state
    const selection = useMemo(() => {
        const keys = Object.keys(state.quarantinedChecked);
        const selectedCount = keys.filter(key => state.quarantinedChecked[key]).length;
        return {
            checked: state.quarantinedChecked,
            selectedCount,
            selectedExtensions: derived.selectedExtensions
        };
    }, [state.quarantinedChecked, derived.selectedExtensions]);

    // Selection actions
    const toggleCheck = useCallback(
        (id: string, checked: boolean) => {
            actions.toggleQuarantinedCheck(id, checked);
        },
        [actions]
    );

    const selectAll = useCallback(() => {
        // Only select scans that are selectable (need review)
        actions.selectAllQuarantined(selectableScans);
    }, [actions, selectableScans]);

    const deselectAll = useCallback(() => {
        actions.deselectAllQuarantined();
    }, [actions]);

    const isAllSelected = useMemo(() => {
        // Check if all selectable scans are selected
        return selectableScans.length > 0 && selectableScans.every(scan => state.quarantinedChecked[scan.id]);
    }, [selectableScans, state.quarantinedChecked]);

    const isSomeSelected = useMemo(() => {
        return selectableScans.some(scan => state.quarantinedChecked[scan.id]) && !isAllSelected;
    }, [selectableScans, state.quarantinedChecked, isAllSelected]);

    // Bulk actions
    const bulkActions = useMemo(
        () => ({
            openAllowDialog: actions.openAllowDialog,
            openBlockDialog: actions.openBlockDialog,
            canPerformBulkAction: selection.selectedCount > 0
        }),
        [actions, selection.selectedCount]
    );

    // Get total count from the counts API (unaffected by search filters and admin decision checkbox filters)
    const totalCount =
        (state.scanCounts?.STARTED ?? 0) +
        (state.scanCounts?.VALIDATING ?? 0) +
        (state.scanCounts?.SCANNING ?? 0) +
        (state.scanCounts?.PASSED ?? 0) +
        (state.scanCounts?.QUARANTINED ?? 0) +
        (state.scanCounts?.AUTO_REJECTED ?? 0) +
        (state.scanCounts?.ERROR ?? 0);

    return useMemo(
        () => ({
            tabIndex: 1,
            tabName: 'Quarantined',
            scans: quarantinedScans,
            isLoading: state.isLoadingScans,
            lastRefreshed: state.lastRefreshed,
            autoRefresh: state.autoRefresh,
            onAutoRefreshChange: actions.setAutoRefresh,
            totalCount,
            search,
            globalFilters,
            quarantineFilters,
            pagination,
            selection,
            toggleCheck,
            selectAll,
            deselectAll,
            isAllSelected,
            isSomeSelected,
            bulkActions
        }),
        [
            quarantinedScans,
            state.isLoadingScans,
            state.lastRefreshed,
            state.autoRefresh,
            actions.setAutoRefresh,
            totalCount,
            search,
            globalFilters,
            quarantineFilters,
            pagination,
            selection,
            toggleCheck,
            selectAll,
            deselectAll,
            isAllSelected,
            isSomeSelected,
            bulkActions
        ]
    );
};

export type UseQuarantinedTabReturn = ReturnType<typeof useQuarantinedTab>;
