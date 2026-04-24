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

import type { Dispatch, MutableRefObject } from "react";
import { useEffect } from 'react';
import { ScanState, ScanAction, ScanResult } from './scan-types';
import { getDateRangeParams, getFileDateRange } from './scan-helpers';

// ============================================================================
// Filter Options Effect
// ============================================================================

/**
 * Hook to fetch filter options (validation types, threat scanners) on mount and refresh
 */
export const useFilterOptionsEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    useEffect(() => {
        const abortController = new AbortController();

        const fetchFilterOptions = async () => {
            try {
                const options = await service.admin.getScanFilterOptions(abortController);
                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_AVAILABLE_VALIDATION_TYPES', payload: options.validationTypes || [] });
                    dispatch({ type: 'SET_AVAILABLE_THREAT_SCANNERS', payload: options.threatScannerNames || [] });
                    dispatch({ type: 'SET_FILTER_OPTIONS_LOADED', payload: true });
                }
            } catch (err: any) {
                if (!abortController.signal.aborted) {
                    // Even on error, mark as loaded so we don't block forever
                    dispatch({ type: 'SET_FILTER_OPTIONS_LOADED', payload: true });
                    handleErrorRef.current(err);
                }
            }
        };

        fetchFilterOptions();
        return () => abortController.abort();
    }, [service, state.refreshTrigger, dispatch, handleErrorRef]);
};

// ============================================================================
// Scans Effect
// ============================================================================

/**
 * Hook to fetch scans from API (tabs 0, 1, 2: Scans, Quarantined, Auto Rejected)
 */
export const useScansEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    useEffect(() => {
        // Only fetch scans for tabs 0, 1, 2
        if (state.selectedTab > 2) {
            return;
        }

        // Wait for filter options to be loaded to avoid duplicate requests
        if (!state.filterOptionsLoaded) {
            return;
        }

        // For Quarantined tab, don't fetch if there are no threat scanners available
        // (we only show scans with threats, so no threat scanners = no data to show)
        if (state.selectedTab === 1 && state.availableThreatScanners.length === 0) {
            dispatch({ type: 'SET_SCANS', payload: { scans: [], totalScans: 0 } });
            dispatch({ type: 'SET_LOADING_SCANS', payload: false });
            return;
        }

        // For Auto Rejected tab, don't fetch if there are no validation types available
        // (we only show scans with validation failures, so no validation types = no data to show)
        if (state.selectedTab === 2 && state.availableValidationTypes.length === 0) {
            dispatch({ type: 'SET_SCANS', payload: { scans: [], totalScans: 0 } });
            dispatch({ type: 'SET_LOADING_SCANS', payload: false });
            return;
        }

        const abortController = new AbortController();

        const fetchScans = async () => {
            // Only show loading state if we don't have existing data to display
            // This allows seamless background refreshes
            if (state.scans.length === 0) {
                dispatch({ type: 'SET_LOADING_SCANS', payload: true });
            }
            try {
                // Determine status filter based on selected tab and status filters
                let statusParam: string[] | string | undefined;
                let validationTypeParam: string[] | undefined;
                let threatScannerParam: string[] | undefined;
                let adminDecisionParam: string[] | undefined;

                if (state.selectedTab === 0) {
                    // Scans tab - use statusFilters, expanding 'running' to explicit statuses
                    statusParam = Array.from(state.statusFilters).reduce((result, status) => {
                        if (status === 'running') {
                            return [...result, 'STARTED', 'VALIDATING', 'SCANNING'];
                        }
                        return [...result, status];
                    }, [] as string[]);
                } else if (state.selectedTab === 1) {
                    // Quarantined tab - show scans with threats
                    // Filter by threat scanner (user selection or all available)
                    if (state.threatScannerFilters.size > 0) {
                        threatScannerParam = Array.from(state.threatScannerFilters);
                    } else {
                        threatScannerParam = state.availableThreatScanners;
                    }
                    // Filter by admin decision status (allowed/blocked/needs-review)
                    if (state.quarantineFilters.size > 0) {
                        adminDecisionParam = Array.from(state.quarantineFilters);
                    }
                } else if (state.selectedTab === 2) {
                    // Auto Rejected tab - show scans with validation failures
                    // Filter by validation type (user selection or all available)
                    if (state.validationTypeFilters.size > 0) {
                        validationTypeParam = Array.from(state.validationTypeFilters);
                    } else {
                        validationTypeParam = state.availableValidationTypes;
                    }
                }

                const dateParams = getDateRangeParams(state.dateRange);

                const response = await service.admin.getAllScans(abortController, {
                    size: state.pageSize,
                    offset: state.currentPage * state.pageSize,
                    publisher: state.publisherQuery || undefined,
                    namespace: state.namespaceQuery || undefined,
                    name: state.nameQuery || undefined,
                    status: statusParam,
                    validationType: validationTypeParam,
                    threatScannerName: threatScannerParam,
                    adminDecision: adminDecisionParam,
                    dateStartedFrom: dateParams.dateStartedFrom,
                    dateStartedTo: dateParams.dateStartedTo,
                    enforcement: state.enforcement
                });

                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_LOADING_SCANS', payload: false });
                    // Convert API response to ScanResult format
                    const convertedScans: ScanResult[] = response.scans.map((scan: any) => ({
                        id: scan.id,
                        displayName: scan.displayName || scan.extensionName,
                        namespace: scan.namespace,
                        extensionName: scan.extensionName,
                        publisher: scan.publisher || '',
                        publisherUrl: scan.publisherUrl || null,
                        version: scan.version,
                        targetPlatform: scan.targetPlatform || 'universal',
                        universalTargetPlatform: scan.universalTargetPlatform ?? true,
                        status: scan.status as any,
                        dateScanStarted: scan.dateScanStarted,
                        dateScanEnded: scan.dateScanEnded || null,
                        dateQuarantined: scan.dateQuarantined || null,
                        dateRejected: scan.dateRejected || null,
                        adminDecision: scan.adminDecision ? {
                            decision: scan.adminDecision.decision,
                            decidedBy: scan.adminDecision.decidedBy,
                            dateDecided: scan.adminDecision.dateDecided,
                        } : null,
                        threats: (scan.threats || []).map((threat: any) => ({
                            id: threat.id,
                            fileName: threat.fileName,
                            fileHash: threat.fileHash,
                            fileExtension: threat.fileExtension,
                            type: threat.type,
                            ruleName: threat.ruleName,
                            severity: threat.severity,
                            enforcedFlag: threat.enforcedFlag ?? true,
                            reason: threat.reason,
                            dateDetected: threat.dateDetected,
                        })),
                        validationFailures: (scan.validationFailures || []).map((failure: any) => ({
                            id: failure.id,
                            type: failure.type,
                            ruleName: failure.ruleName,
                            reason: failure.reason,
                            dateDetected: failure.dateDetected,
                            enforcedFlag: failure.enforcedFlag ?? true,
                        })),
                        checkResults: (scan.checkResults || []).map((result: any) => ({
                            checkType: result.checkType,
                            category: result.category,
                            result: result.result,
                            startedAt: result.startedAt,
                            completedAt: result.completedAt || null,
                            durationMs: result.durationMs ?? null,
                            filesScanned: result.filesScanned || null,
                            findingsCount: result.findingsCount || null,
                            summary: result.summary || null,
                            errorMessage: result.errorMessage || null,
                            required: result.required ?? null,
                            externalUrl: result.externalUrl || null,
                        })),
                        extensionIcon: scan.extensionIcon,
                        downloadUrl: scan.downloadUrl || null,
                        errorMessage: scan.errorMessage || null,
                    }));

                    dispatch({ type: 'SET_SCANS', payload: { scans: convertedScans, totalScans: response.totalSize } });
                    dispatch({ type: 'SET_LAST_REFRESHED', payload: new Date() });
                }
            } catch (err: any) {
                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_LOADING_SCANS', payload: false });
                    handleErrorRef.current(err);
                }
            }
        };

        fetchScans();
        return () => abortController.abort();
    }, [
        service,
        state.filterOptionsLoaded,
        state.currentPage,
        state.pageSize,
        state.publisherQuery,
        state.namespaceQuery,
        state.nameQuery,
        state.selectedTab,
        state.statusFilters,
        state.quarantineFilters,
        state.validationTypeFilters,
        state.threatScannerFilters,
        state.availableThreatScanners,
        state.availableValidationTypes,
        state.dateRange,
        state.enforcement,
        state.refreshTrigger,
        dispatch,
        handleErrorRef,
    ]);
};

// ============================================================================
// Scan Counts Effect
// ============================================================================

/**
 * Hook to fetch scan counts from API
 * Uses the same tab-aware filtering logic as the scans list
 */
export const useScanCountsEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    useEffect(() => {
        const abortController = new AbortController();

        const fetchCounts = async () => {
            try {
                const dateParams = getDateRangeParams(state.dateRange);

                // Determine filters based on selected tab (same logic as scans fetch)
                let validationTypeParam: string[] | undefined;
                let threatScannerParam: string[] | undefined;

                if (state.selectedTab === 0) {
                    // Scans tab - no threatScanner or validationType filters
                    // (same as scans endpoint which only uses statusFilters on this tab)
                } else if (state.selectedTab === 1) {
                    // Quarantined tab - filter by threat scanner (user selection or all available)
                    if (state.threatScannerFilters.size > 0) {
                        threatScannerParam = Array.from(state.threatScannerFilters);
                    } else {
                        threatScannerParam = state.availableThreatScanners;
                    }
                } else if (state.selectedTab === 2) {
                    // Auto Rejected tab - filter by validation type (user selection or all available)
                    if (state.validationTypeFilters.size > 0) {
                        validationTypeParam = Array.from(state.validationTypeFilters);
                    } else {
                        validationTypeParam = state.availableValidationTypes;
                    }
                }

                const counts = await service.admin.getScanCounts(abortController, {
                    ...dateParams,
                    enforcement: state.enforcement,
                    threatScannerName: threatScannerParam,
                    validationType: validationTypeParam,
                });

                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_SCAN_COUNTS', payload: counts });
                }
            } catch (err: any) {
                if (!abortController.signal.aborted) {
                    handleErrorRef.current(err);
                }
            }
        };

        fetchCounts();
        return () => abortController.abort();
    }, [
        service,
        state.dateRange,
        state.enforcement,
        state.threatScannerFilters,
        state.validationTypeFilters,
        state.selectedTab,
        state.availableThreatScanners,
        state.availableValidationTypes,
        state.refreshTrigger,
        dispatch,
        handleErrorRef,
    ]);
};

// ============================================================================
// Files Effect
// ============================================================================

/**
 * Hook to fetch files from API (tabs 3, 4: Allowed Files, Blocked Files)
 */
export const useFilesEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    useEffect(() => {
        // Only fetch files for tabs 3 and 4
        if (state.selectedTab < 3) {
            return;
        }

        const abortController = new AbortController();

        const fetchFiles = async () => {
            // Only show loading state if we don't have existing data to display
            // (Avoids flashing loading indicator when auto-refreshing)
            if (state.files.length === 0) {
                dispatch({ type: 'SET_LOADING_FILES', payload: true });
            }
            try {
                // Determine decision filter based on selected tab
                const decisionParam = state.selectedTab === 3 ? 'allowed' : 'blocked';

                // Get date range parameters
                const dateParams = getFileDateRange(state.fileDateRange);

                const response = await service.admin.getFiles(abortController, {
                    size: state.pageSize,
                    offset: state.currentPage * state.pageSize,
                    publisher: state.publisherQuery || undefined,
                    namespace: state.namespaceQuery || undefined,
                    name: state.nameQuery || undefined,
                    decision: decisionParam,
                    dateDecidedFrom: dateParams.dateDecidedFrom,
                    dateDecidedTo: dateParams.dateDecidedTo,
                });

                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_LOADING_FILES', payload: false });
                    dispatch({ type: 'SET_FILES', payload: { files: response.files, totalFiles: response.totalSize } });
                    dispatch({ type: 'SET_LAST_REFRESHED', payload: new Date() });
                }
            } catch (err: any) {
                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_LOADING_FILES', payload: false });
                    handleErrorRef.current(err);
                }
            }
        };

        fetchFiles();
        return () => abortController.abort();
    }, [
        service,
        state.currentPage,
        state.pageSize,
        state.publisherQuery,
        state.namespaceQuery,
        state.nameQuery,
        state.selectedTab,
        state.fileDateRange,
        state.refreshTrigger,
        state.files.length,
        dispatch,
        handleErrorRef,
    ]);
};

// ============================================================================
// File Counts Effect
// ============================================================================

/**
 * Hook to fetch file counts from API
 */
export const useFileCountsEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    useEffect(() => {
        // Only fetch file counts when on file tabs
        if (state.selectedTab < 3) {
            return;
        }

        const abortController = new AbortController();

        const fetchFileCounts = async () => {
            try {
                // Get date range parameters
                const dateParams = getFileDateRange(state.fileDateRange);

                const counts = await service.admin.getFileCounts(abortController, {
                    dateDecidedFrom: dateParams.dateDecidedFrom,
                    dateDecidedTo: dateParams.dateDecidedTo,
                });

                if (!abortController.signal.aborted) {
                    dispatch({ type: 'SET_FILE_COUNTS', payload: counts });
                }
            } catch (err: any) {
                if (!abortController.signal.aborted) {
                    handleErrorRef.current(err);
                }
            }
        };

        fetchFileCounts();
        return () => abortController.abort();
    }, [
        service,
        state.selectedTab,
        state.fileDateRange,
        state.refreshTrigger,
        dispatch,
        handleErrorRef,
    ]);
};

// ============================================================================
// Auto Refresh Effect
// ============================================================================

/**
 * Hook for periodic refresh - refreshes data every 30 seconds when enabled and page is visible
 */
export const useAutoRefreshEffect = (
    state: ScanState,
    dispatch: Dispatch<ScanAction>
) => {
    useEffect(() => {
        if (!state.autoRefresh) {
            return; // Don't set up interval if auto-refresh is disabled
        }

        const REFRESH_INTERVAL = 30000; // 30 seconds

        const intervalId = setInterval(() => {
            // Only refresh when page is visible
            if (!document.hidden) {
                dispatch({ type: 'TRIGGER_REFRESH' });
            }
        }, REFRESH_INTERVAL);

        return () => {
            clearInterval(intervalId);
        };
    }, [state.autoRefresh, dispatch]);
};
