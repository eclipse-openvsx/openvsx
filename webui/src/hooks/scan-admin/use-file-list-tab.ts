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
import { useScanContext, FileDecisionType } from '../../context/scan-admin';
import { usePagination } from './use-pagination';
import { useSearch } from './use-search';

interface UseFileListTabOptions {
    tabIndex: 4 | 5;
    decisionType: FileDecisionType;
    tabName: string;
}

/**
 * Hook for the Allowed Files (tab 3) and Blocked Files (tab 4) tabs.
 * Provides file list data, selection management, and file actions.
 */
export const useFileListTab = ({ tabIndex, decisionType, tabName }: UseFileListTabOptions) => {
    const { state, actions, derived } = useScanContext();
    const pagination = usePagination();
    const search = useSearch();

    // Files are already filtered server-side by decision parameter
    const files = state.files;

    // Auto-refresh state (shared with all tabs)
    const lastRefreshed = state.lastRefreshed;
    const autoRefresh = state.autoRefresh;
    const onAutoRefreshChange = actions.setAutoRefresh;

    // Selection state (files already filtered server-side by decision)
    const selection = useMemo(
        () => ({
            checked: state.filesChecked,
            selectedCount: state.filesChecked.size,
            selectedFiles: derived.selectedFiles
        }),
        [state.filesChecked, derived.selectedFiles]
    );

    // Selection actions
    const setFilesChecked = useCallback(
        (fileIds: Set<string>) => {
            actions.setFilesChecked(fileIds);
        },
        [actions]
    );

    const selectAll = useCallback(() => {
        const allIds = new Set(files.map(f => f.id));
        actions.setFilesChecked(allIds);
    }, [files, actions]);

    const deselectAll = useCallback(() => {
        actions.setFilesChecked(new Set());
    }, [actions]);

    const isAllSelected = useMemo(() => {
        return files.length > 0 && files.every(file => state.filesChecked.has(file.id));
    }, [files, state.filesChecked]);

    const isSomeSelected = useMemo(() => {
        return files.some(file => state.filesChecked.has(file.id)) && !isAllSelected;
    }, [files, state.filesChecked, isAllSelected]);

    // File actions
    const fileActions = useMemo(
        () => ({
            openAllowDialog: () => actions.openFileDialog('allow'),
            openBlockDialog: () => actions.openFileDialog('block'),
            openDeleteDialog: () => actions.openFileDialog('delete'),
            canPerformAction: selection.selectedCount > 0
        }),
        [actions, selection.selectedCount]
    );

    // Get count from file counts
    const fileCount = state.fileCounts
        ? decisionType === 'allowed'
            ? state.fileCounts.allowed
            : state.fileCounts.blocked
        : 0;

    return useMemo(
        () => ({
            tabIndex,
            tabName,
            decisionType,
            files,
            isLoading: state.isLoadingFiles,
            fileCount,
            lastRefreshed,
            autoRefresh,
            onAutoRefreshChange,
            search,
            pagination,
            selection,
            setFilesChecked,
            selectAll,
            deselectAll,
            isAllSelected,
            isSomeSelected,
            fileActions
        }),
        [
            tabIndex,
            tabName,
            decisionType,
            files,
            state.isLoadingFiles,
            fileCount,
            lastRefreshed,
            autoRefresh,
            onAutoRefreshChange,
            search,
            pagination,
            selection,
            setFilesChecked,
            selectAll,
            deselectAll,
            isAllSelected,
            isSomeSelected,
            fileActions
        ]
    );
};

/**
 * Hook specifically for the Allow List tab (tab index 4).
 */
export const useAllowListTab = () => {
    return useFileListTab({
        tabIndex: 4,
        decisionType: 'allowed',
        tabName: 'Allow List'
    });
};

/**
 * Hook specifically for the Block List tab (tab index 5).
 */
export const useBlockListTab = () => {
    return useFileListTab({
        tabIndex: 5,
        decisionType: 'blocked',
        tabName: 'Block List'
    });
};

export type UseFileListTabReturn = ReturnType<typeof useFileListTab>;
export type UseAllowListTabReturn = ReturnType<typeof useAllowListTab>;
export type UseBlockListTabReturn = ReturnType<typeof useBlockListTab>;
