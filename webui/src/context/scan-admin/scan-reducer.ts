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

import { ScanState, ScanAction } from './scan-types';

/**
 * Helper function to toggle a value in a Set
 */
const toggleSetValue = <T>(set: Set<T>, value: T): Set<T> => {
    const newSet = new Set(set);
    if (newSet.has(value)) {
        newSet.delete(value);
    } else {
        newSet.add(value);
    }
    return newSet;
};

/**
 * Reducer for scan state management
 */
export const scanReducer = (state: ScanState, action: ScanAction): ScanState => {
    switch (action.type) {
        // ============================================================
        // Tab actions
        // ============================================================
        case 'SET_TAB': {
            const tab = action.payload;
            // Tab-specific default enforcement:
            // - Tabs 1 (Quarantined) and 2 (Auto Rejected): default to 'enforced'
            // - Other tabs: default to 'all'
            const defaultEnforcement = tab === 1 || tab === 2 ? 'enforced' : 'all';
            return {
                ...state,
                selectedTab: tab,
                // Clear search when switching tabs
                publisherQuery: '',
                namespaceQuery: '',
                nameQuery: '',
                // Reset page
                currentPage: 0,
                // Reset to tab-specific default filters
                dateRange: 'all',
                enforcement: defaultEnforcement,
                quarantineFilters: new Set(),
                statusFilters: new Set(),
                // Clear scans and show loading state to prevent stale data flash
                scans: [],
                isLoadingScans: true,
                // Clear files and show loading state to prevent stale data flash
                files: [],
                isLoadingFiles: tab >= 4, // Set loading state for file tabs (4, 5)
                // Clear counts to prevent stale counts from showing
                scanCounts: null,
                fileCounts: null,
                // Clear selection state to prevent stale counts
                quarantinedChecked: {},
                filesChecked: new Set()
            };
        }

        // ============================================================
        // Search actions
        // ============================================================
        case 'SET_PUBLISHER_QUERY':
            return {
                ...state,
                publisherQuery: action.payload,
                currentPage: 0,
                quarantinedChecked: {},
                filesChecked: new Set<string>()
            };

        case 'SET_NAMESPACE_QUERY':
            return {
                ...state,
                namespaceQuery: action.payload,
                currentPage: 0,
                quarantinedChecked: {},
                filesChecked: new Set<string>()
            };

        case 'SET_NAME_QUERY':
            return {
                ...state,
                nameQuery: action.payload,
                currentPage: 0,
                quarantinedChecked: {},
                filesChecked: new Set<string>()
            };

        case 'CLEAR_SEARCH':
            return {
                ...state,
                publisherQuery: '',
                namespaceQuery: '',
                nameQuery: '',
                filesChecked: new Set<string>()
            };

        // ============================================================
        // Pagination actions
        // ============================================================
        case 'SET_PAGE':
            return { ...state, currentPage: action.payload, quarantinedChecked: {}, filesChecked: new Set<string>() };

        case 'RESET_PAGE':
            return { ...state, currentPage: 0 };

        // ============================================================
        // Global filter actions
        // ============================================================
        case 'SET_DATE_RANGE':
            return {
                ...state,
                dateRange: action.payload,
                currentPage: 0,
                quarantinedChecked: {},
                filesChecked: new Set()
            };

        case 'SET_ENFORCEMENT':
            return {
                ...state,
                enforcement: action.payload,
                currentPage: 0,
                quarantinedChecked: {}
            };

        case 'SET_FILE_DATE_RANGE':
            return { ...state, fileDateRange: action.payload, currentPage: 0, filesChecked: new Set<string>() };

        // ============================================================
        // Tab-specific filter actions
        // ============================================================
        case 'TOGGLE_STATUS_FILTER':
            return {
                ...state,
                statusFilters: toggleSetValue(state.statusFilters, action.payload),
                currentPage: 0
            };

        case 'SET_STATUS_FILTERS':
            return { ...state, statusFilters: action.payload, currentPage: 0 };

        case 'TOGGLE_QUARANTINE_FILTER':
            return {
                ...state,
                quarantineFilters: toggleSetValue(state.quarantineFilters, action.payload),
                currentPage: 0,
                quarantinedChecked: {}
            };

        case 'SET_QUARANTINE_FILTERS':
            return { ...state, quarantineFilters: action.payload, currentPage: 0, quarantinedChecked: {} };

        case 'TOGGLE_THREAT_SCANNER_FILTER':
            return {
                ...state,
                threatScannerFilters: toggleSetValue(state.threatScannerFilters, action.payload),
                currentPage: 0,
                quarantinedChecked: {}
            };

        case 'SET_THREAT_SCANNER_FILTERS':
            return { ...state, threatScannerFilters: action.payload, currentPage: 0, quarantinedChecked: {} };

        case 'TOGGLE_VALIDATION_TYPE_FILTER':
            return {
                ...state,
                validationTypeFilters: toggleSetValue(state.validationTypeFilters, action.payload),
                currentPage: 0
            };

        case 'SET_VALIDATION_TYPE_FILTERS':
            return { ...state, validationTypeFilters: action.payload, currentPage: 0 };

        // ============================================================
        // Filter options actions (from API)
        // ============================================================
        case 'SET_FILTER_OPTIONS_LOADED':
            return { ...state, filterOptionsLoaded: action.payload };

        case 'SET_AVAILABLE_VALIDATION_TYPES':
            return { ...state, availableValidationTypes: action.payload };

        case 'SET_AVAILABLE_THREAT_SCANNERS':
            return { ...state, availableThreatScanners: action.payload };

        // ============================================================
        // Menu anchor actions
        // ============================================================
        case 'SET_FILTER_MENU_ANCHOR':
            return { ...state, filterMenuAnchor: action.payload };

        case 'SET_QUARANTINE_FILTER_MENU_ANCHOR':
            return { ...state, quarantineFilterMenuAnchor: action.payload };

        case 'SET_AUTO_REJECTED_FILTER_MENU_ANCHOR':
            return { ...state, autoRejectedFilterMenuAnchor: action.payload };

        // ============================================================
        // Selection actions
        // ============================================================
        case 'SET_QUARANTINED_CHECKED':
            return { ...state, quarantinedChecked: action.payload };

        case 'TOGGLE_QUARANTINED_CHECKED':
            return {
                ...state,
                quarantinedChecked: {
                    ...state.quarantinedChecked,
                    [action.payload.id]: action.payload.checked
                }
            };

        case 'SELECT_ALL_QUARANTINED': {
            const newChecked: Record<string, boolean> = {};
            action.payload.forEach(scan => {
                newChecked[scan.id] = true;
            });
            return { ...state, quarantinedChecked: newChecked };
        }

        case 'DESELECT_ALL_QUARANTINED':
            return { ...state, quarantinedChecked: {} };

        case 'SET_SCAN_DECISIONS':
            return { ...state, scanDecisions: action.payload };

        case 'SET_FILES_CHECKED':
            return { ...state, filesChecked: action.payload };

        // ============================================================
        // Dialog actions
        // ============================================================
        case 'OPEN_CONFIRM_DIALOG':
            return {
                ...state,
                confirmDialogOpen: true,
                confirmAction: action.payload
            };

        case 'CLOSE_CONFIRM_DIALOG':
            return {
                ...state,
                confirmDialogOpen: false,
                confirmAction: null
            };

        case 'OPEN_FILE_DIALOG':
            return {
                ...state,
                fileDialogOpen: true,
                fileActionType: action.payload
            };

        case 'CLOSE_FILE_DIALOG':
            return {
                ...state,
                fileDialogOpen: false,
                fileActionType: null
            };

        // ============================================================
        // Data actions (from API)
        // ============================================================
        case 'SET_SCANS':
            return {
                ...state,
                scans: action.payload.scans,
                totalScans: action.payload.totalScans
            };

        case 'SET_LOADING_SCANS':
            return { ...state, isLoadingScans: action.payload };

        case 'SET_SCAN_COUNTS':
            return { ...state, scanCounts: action.payload };

        case 'TRIGGER_REFRESH':
            return { ...state, refreshTrigger: state.refreshTrigger + 1 };

        case 'SET_LAST_REFRESHED':
            return { ...state, lastRefreshed: action.payload };

        case 'SET_AUTO_REFRESH':
            return { ...state, autoRefresh: action.payload };

        // ============================================================
        // File data actions (from /files API)
        // ============================================================
        case 'SET_FILES':
            return {
                ...state,
                files: action.payload.files,
                totalFiles: action.payload.totalFiles
            };

        case 'SET_LOADING_FILES':
            return { ...state, isLoadingFiles: action.payload };

        case 'SET_FILE_COUNTS':
            return { ...state, fileCounts: action.payload };

        // ============================================================
        // Confirm action execution
        // ============================================================
        case 'EXECUTE_CONFIRM_ACTION': {
            if (state.confirmAction === 'allow' || state.confirmAction === 'block') {
                const newDecisions = { ...state.scanDecisions };
                for (const id in state.quarantinedChecked) {
                    if (state.quarantinedChecked[id]) {
                        newDecisions[id] = state.confirmAction === 'allow' ? 'allowed' : 'blocked';
                    }
                }
                return {
                    ...state,
                    scanDecisions: newDecisions,
                    quarantinedChecked: {},
                    confirmDialogOpen: false,
                    confirmAction: null
                };
            }
            return {
                ...state,
                confirmDialogOpen: false,
                confirmAction: null
            };
        }

        default:
            return state;
    }
};
