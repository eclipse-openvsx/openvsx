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

import { useEffect, useRef, useCallback } from 'react';
import { useScanContext } from '../../context/scan-admin';
import { DateRangeType, EnforcementType } from '../../context/scan-admin';

/**
 * URL parameter keys
 */
const URL_PARAMS = {
    TAB: 'tab',
    PAGE: 'page',
    PUBLISHER: 'publisher',
    NAMESPACE: 'namespace',
    NAME: 'name',
    DATE_RANGE: 'dateRange',
    FILE_DATE_RANGE: 'fileDateRange',
    ENFORCEMENT: 'enforcement',
    // Checkbox filters
    STATUS_FILTERS: 'status',
    QUARANTINE_FILTERS: 'quarantine',
    THREAT_SCANNER_FILTERS: 'threatScanner',
    VALIDATION_TYPE_FILTERS: 'validationType'
} as const;

/**
 * Tab name to index mapping
 */
const TAB_NAME_TO_INDEX: Record<string, number> = {
    scans: 0,
    quarantined: 1,
    'auto-rejected': 2,
    errors: 3,
    'allowed-files': 4,
    'blocked-files': 5
};

const TAB_INDEX_TO_NAME: Record<number, string> = {
    0: 'scans',
    1: 'quarantined',
    2: 'auto-rejected',
    3: 'errors',
    4: 'allowed-files',
    5: 'blocked-files'
};

/**
 * Valid date range values
 */
const VALID_DATE_RANGES: DateRangeType[] = ['today', 'last7days', 'last30days', 'last90days', 'all'];

/**
 * Valid enforcement values
 */
const VALID_ENFORCEMENTS: EnforcementType[] = ['enforced', 'notEnforced', 'all'];

/**
 * Hook to sync scan admin state with URL parameters.
 * This enables bookmarkable URLs and browser back/forward navigation.
 */
export const useUrlSync = () => {
    const { state, dispatch } = useScanContext();
    const isInitialized = useRef(false);
    const isUpdatingFromUrl = useRef(false);

    /**
     * Parse URL parameters and apply to state on initial load
     */
    const initializeFromUrl = useCallback(() => {
        const params = new URLSearchParams(window.location.search);

        // Tab
        const tabParam = params.get(URL_PARAMS.TAB);
        if (tabParam && TAB_NAME_TO_INDEX[tabParam] !== undefined) {
            dispatch({ type: 'SET_TAB', payload: TAB_NAME_TO_INDEX[tabParam] });
        }

        // Page (URL is 1-indexed, state is 0-indexed)
        const pageParam = params.get(URL_PARAMS.PAGE);
        if (pageParam) {
            const page = parseInt(pageParam, 10);
            if (!isNaN(page) && page >= 1) {
                dispatch({ type: 'SET_PAGE', payload: page - 1 });
            }
        }

        // Search queries
        const publisherParam = params.get(URL_PARAMS.PUBLISHER);
        if (publisherParam) {
            dispatch({ type: 'SET_PUBLISHER_QUERY', payload: publisherParam });
        }

        const namespaceParam = params.get(URL_PARAMS.NAMESPACE);
        if (namespaceParam) {
            dispatch({ type: 'SET_NAMESPACE_QUERY', payload: namespaceParam });
        }

        const nameParam = params.get(URL_PARAMS.NAME);
        if (nameParam) {
            dispatch({ type: 'SET_NAME_QUERY', payload: nameParam });
        }

        // Date range
        const dateRangeParam = params.get(URL_PARAMS.DATE_RANGE);
        if (dateRangeParam && VALID_DATE_RANGES.indexOf(dateRangeParam as DateRangeType) !== -1) {
            dispatch({ type: 'SET_DATE_RANGE', payload: dateRangeParam as DateRangeType });
        }

        // File date range
        const fileDateRangeParam = params.get(URL_PARAMS.FILE_DATE_RANGE);
        if (fileDateRangeParam && VALID_DATE_RANGES.indexOf(fileDateRangeParam as DateRangeType) !== -1) {
            dispatch({ type: 'SET_FILE_DATE_RANGE', payload: fileDateRangeParam as DateRangeType });
        }

        // Enforcement
        const enforcementParam = params.get(URL_PARAMS.ENFORCEMENT);
        if (enforcementParam && VALID_ENFORCEMENTS.indexOf(enforcementParam as EnforcementType) !== -1) {
            dispatch({ type: 'SET_ENFORCEMENT', payload: enforcementParam as EnforcementType });
        }

        // Status filters (comma-separated)
        const statusFiltersParam = params.get(URL_PARAMS.STATUS_FILTERS);
        if (statusFiltersParam) {
            const filters = new Set(statusFiltersParam.split(',').filter(Boolean));
            if (filters.size > 0) {
                dispatch({ type: 'SET_STATUS_FILTERS', payload: filters });
            }
        }

        // Quarantine filters (comma-separated)
        const quarantineFiltersParam = params.get(URL_PARAMS.QUARANTINE_FILTERS);
        if (quarantineFiltersParam) {
            const filters = new Set(quarantineFiltersParam.split(',').filter(Boolean));
            if (filters.size > 0) {
                dispatch({ type: 'SET_QUARANTINE_FILTERS', payload: filters });
            }
        }

        // Threat scanner filters (comma-separated)
        const threatScannerFiltersParam = params.get(URL_PARAMS.THREAT_SCANNER_FILTERS);
        if (threatScannerFiltersParam) {
            const filters = new Set(threatScannerFiltersParam.split(',').filter(Boolean));
            if (filters.size > 0) {
                dispatch({ type: 'SET_THREAT_SCANNER_FILTERS', payload: filters });
            }
        }

        // Validation type filters (comma-separated)
        const validationTypeFiltersParam = params.get(URL_PARAMS.VALIDATION_TYPE_FILTERS);
        if (validationTypeFiltersParam) {
            const filters = new Set(validationTypeFiltersParam.split(',').filter(Boolean));
            if (filters.size > 0) {
                dispatch({ type: 'SET_VALIDATION_TYPE_FILTERS', payload: filters });
            }
        }
    }, [dispatch]);

    /**
     * Update URL parameters from state
     */
    const updateUrlFromState = useCallback(() => {
        if (isUpdatingFromUrl.current) {
            return;
        }

        const params = new URLSearchParams();

        // Tab (only add if not default)
        if (state.selectedTab !== 0) {
            params.set(URL_PARAMS.TAB, TAB_INDEX_TO_NAME[state.selectedTab] || 'scans');
        }

        // Page (only add if not first page, URL is 1-indexed)
        if (state.currentPage > 0) {
            params.set(URL_PARAMS.PAGE, String(state.currentPage + 1));
        }

        // Search queries (only add if not empty)
        if (state.publisherQuery) {
            params.set(URL_PARAMS.PUBLISHER, state.publisherQuery);
        }
        if (state.namespaceQuery) {
            params.set(URL_PARAMS.NAMESPACE, state.namespaceQuery);
        }
        if (state.nameQuery) {
            params.set(URL_PARAMS.NAME, state.nameQuery);
        }

        // Date range (only add if not default)
        if (state.dateRange !== 'all') {
            params.set(URL_PARAMS.DATE_RANGE, state.dateRange);
        }

        // File date range (only add if not default)
        if (state.fileDateRange !== 'all') {
            params.set(URL_PARAMS.FILE_DATE_RANGE, state.fileDateRange);
        }

        // Enforcement (only add if not tab-specific default)
        // Tabs 1 (Quarantined) and 2 (Auto Rejected) default to 'enforced', others default to 'all'
        const defaultEnforcement = state.selectedTab === 1 || state.selectedTab === 2 ? 'enforced' : 'all';
        if (state.enforcement !== defaultEnforcement) {
            params.set(URL_PARAMS.ENFORCEMENT, state.enforcement);
        }

        // Status filters (only add if any are selected)
        if (state.statusFilters.size > 0) {
            params.set(URL_PARAMS.STATUS_FILTERS, Array.from(state.statusFilters).join(','));
        }

        // Quarantine filters (only add if any are selected - default is empty)
        if (state.quarantineFilters.size > 0) {
            params.set(URL_PARAMS.QUARANTINE_FILTERS, Array.from(state.quarantineFilters).join(','));
        }

        // Threat scanner filters (only add if any are selected)
        if (state.threatScannerFilters.size > 0) {
            params.set(URL_PARAMS.THREAT_SCANNER_FILTERS, Array.from(state.threatScannerFilters).join(','));
        }

        // Validation type filters (only add if any are selected)
        if (state.validationTypeFilters.size > 0) {
            params.set(URL_PARAMS.VALIDATION_TYPE_FILTERS, Array.from(state.validationTypeFilters).join(','));
        }

        // Build new URL
        const newSearch = params.toString();
        const newUrl = newSearch ? `${window.location.pathname}?${newSearch}` : window.location.pathname;

        // Update URL without triggering a page reload
        if (window.location.search !== (newSearch ? `?${newSearch}` : '')) {
            window.history.replaceState(null, '', newUrl);
        }
    }, [
        state.selectedTab,
        state.currentPage,
        state.publisherQuery,
        state.namespaceQuery,
        state.nameQuery,
        state.dateRange,
        state.fileDateRange,
        state.enforcement,
        state.statusFilters,
        state.quarantineFilters,
        state.threatScannerFilters,
        state.validationTypeFilters
    ]);

    /**
     * Handle browser back/forward navigation
     */
    const handlePopState = useCallback(() => {
        isUpdatingFromUrl.current = true;
        initializeFromUrl();
        // Reset the flag after a short delay to allow state to settle
        setTimeout(() => {
            isUpdatingFromUrl.current = false;
        }, 100);
    }, [initializeFromUrl]);

    // Initialize from URL on mount
    useEffect(() => {
        if (!isInitialized.current) {
            isInitialized.current = true;
            isUpdatingFromUrl.current = true;
            initializeFromUrl();
            // Reset the flag after initialization
            setTimeout(() => {
                isUpdatingFromUrl.current = false;
            }, 100);
        }
    }, [initializeFromUrl]);

    // Update URL when state changes
    useEffect(() => {
        if (isInitialized.current) {
            updateUrlFromState();
        }
    }, [updateUrlFromState]);

    // Listen for browser back/forward
    useEffect(() => {
        window.addEventListener('popstate', handlePopState);
        return () => {
            window.removeEventListener('popstate', handlePopState);
        };
    }, [handlePopState]);

    return {
        initializeFromUrl,
        updateUrlFromState
    };
};

export type UseUrlSyncReturn = ReturnType<typeof useUrlSync>;
