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
 * Hook for managing pagination state and actions.
 * Provides page navigation, total pages calculation, and loading state.
 */
export const usePagination = () => {
    const { state, actions, derived } = useScanContext();

    const goToPage = useCallback(
        (page: number) => {
            if (page >= 0 && page < derived.totalPages) {
                actions.setPage(page);
            }
        },
        [actions, derived.totalPages]
    );

    const goToNextPage = useCallback(() => {
        if (state.currentPage < derived.totalPages - 1) {
            actions.setPage(state.currentPage + 1);
        }
    }, [state.currentPage, derived.totalPages, actions]);

    const goToPreviousPage = useCallback(() => {
        if (state.currentPage > 0) {
            actions.setPage(state.currentPage - 1);
        }
    }, [state.currentPage, actions]);

    const goToFirstPage = useCallback(() => {
        actions.setPage(0);
    }, [actions]);

    const goToLastPage = useCallback(() => {
        if (derived.totalPages > 0) {
            actions.setPage(derived.totalPages - 1);
        }
    }, [derived.totalPages, actions]);

    // Determine if we're in a loading state based on selected tab
    const isLoading = state.selectedTab >= 4 ? state.isLoadingFiles : state.isLoadingScans;

    // Get total items based on selected tab
    const totalItems = state.selectedTab >= 4 ? state.totalFiles : state.totalScans;

    return useMemo(
        () => ({
            currentPage: state.currentPage,
            pageSize: state.pageSize,
            totalPages: derived.totalPages,
            totalItems,
            isLoading,
            goToPage,
            goToNextPage,
            goToPreviousPage,
            goToFirstPage,
            goToLastPage,
            hasNextPage: state.currentPage < derived.totalPages - 1,
            hasPreviousPage: state.currentPage > 0,
            isFirstPage: state.currentPage === 0,
            isLastPage: state.currentPage >= derived.totalPages - 1,
            // Range info (1-indexed for display)
            startItem: totalItems > 0 ? state.currentPage * state.pageSize + 1 : 0,
            endItem: Math.min((state.currentPage + 1) * state.pageSize, totalItems)
        }),
        [
            state.currentPage,
            state.pageSize,
            derived.totalPages,
            totalItems,
            isLoading,
            goToPage,
            goToNextPage,
            goToPreviousPage,
            goToFirstPage,
            goToLastPage
        ]
    );
};

export type UsePaginationReturn = ReturnType<typeof usePagination>;
