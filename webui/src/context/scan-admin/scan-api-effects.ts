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

import type { Dispatch, MutableRefObject } from 'react';
import { useEffect } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ScanState, ScanAction, ScanResult } from './scan-types';
import { getDateRangeParams, getFileDateRange } from './scan-helpers';
import { controllerFromSignal } from '../../query-client';

// These views show live moderation data and rely on an explicit refresh trigger,
// so every query is keyed on `state.refreshTrigger` and kept fresh (`staleTime: 0`).
// `keepPreviousData` preserves the current rows during background refreshes and
// tab/filter changes, matching the previous "only show loading without data"
// behaviour, which we mirror by dispatching the query's `isLoading` flag.

/**
 * Converts a raw /scans API response into the ScanResult[] shape used by the reducer.
 */
const convertScans = (response: any): { scans: ScanResult[]; totalScans: number } => {
    const scans: ScanResult[] = response.scans.map((scan: any) => ({
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
        adminDecision: scan.adminDecision
            ? {
                  decision: scan.adminDecision.decision,
                  decidedBy: scan.adminDecision.decidedBy,
                  dateDecided: scan.adminDecision.dateDecided
              }
            : null,
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
            dateDetected: threat.dateDetected
        })),
        validationFailures: (scan.validationFailures || []).map((failure: any) => ({
            id: failure.id,
            type: failure.type,
            ruleName: failure.ruleName,
            reason: failure.reason,
            dateDetected: failure.dateDetected,
            enforcedFlag: failure.enforcedFlag ?? true
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
            externalUrl: result.externalUrl || null
        })),
        scannerJobs: (scan.scannerJobs || []).map((job: any) => ({
            id: job.id,
            scannerType: job.scannerType,
            status: job.status,
            createdAt: job.createdAt,
            updatedAt: job.updatedAt,
            errorMessage: job.errorMessage || null,
            externalUrl: job.externalUrl || null
        })),
        extensionIcon: scan.extensionIcon,
        downloadUrl: scan.downloadUrl || null,
        errorMessage: scan.errorMessage || null
    }));

    return { scans, totalScans: response.totalSize };
};

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
    const { data, error } = useQuery({
        queryKey: ['admin', 'scan', 'filter-options', state.refreshTrigger],
        queryFn: ({ signal }) => service.admin.getScanFilterOptions(controllerFromSignal(signal)),
        staleTime: 0
    });

    useEffect(() => {
        if (data) {
            dispatch({ type: 'SET_AVAILABLE_VALIDATION_TYPES', payload: data.validationTypes || [] });
            dispatch({ type: 'SET_AVAILABLE_THREAT_SCANNERS', payload: data.threatScannerNames || [] });
            dispatch({ type: 'SET_FILTER_OPTIONS_LOADED', payload: true });
        }
    }, [data, dispatch]);

    useEffect(() => {
        if (error) {
            // Even on error, mark as loaded so we don't block forever
            dispatch({ type: 'SET_FILTER_OPTIONS_LOADED', payload: true });
            handleErrorRef.current(error);
        }
    }, [error, dispatch, handleErrorRef]);
};

// ============================================================================
// Scans Effect
// ============================================================================

/**
 * Hook to fetch scans from API (tabs 0-3: Scans, Quarantined, Auto Rejected, Errors)
 */
export const useScansEffect = (
    service: any,
    state: ScanState,
    dispatch: Dispatch<ScanAction>,
    handleErrorRef: MutableRefObject<(error: any) => void>
) => {
    // For the Quarantined/Auto Rejected tabs there is nothing to show when no
    // threat scanners / validation types exist, so we skip the request entirely.
    const noQuarantineData = state.selectedTab === 1 && state.availableThreatScanners.length === 0;
    const noAutoRejectedData = state.selectedTab === 2 && state.availableValidationTypes.length === 0;
    const enabled = state.selectedTab <= 3 && state.filterOptionsLoaded && !noQuarantineData && !noAutoRejectedData;

    const dateParams = getDateRangeParams(state.dateRange);

    const { data, error, isLoading } = useQuery({
        queryKey: [
            'admin',
            'scan',
            'scans',
            state.selectedTab,
            state.currentPage,
            state.pageSize,
            state.publisherQuery,
            state.namespaceQuery,
            state.nameQuery,
            Array.from(state.statusFilters),
            Array.from(state.quarantineFilters),
            Array.from(state.validationTypeFilters),
            Array.from(state.threatScannerFilters),
            state.availableThreatScanners,
            state.availableValidationTypes,
            state.dateRange,
            state.enforcement,
            state.refreshTrigger
        ],
        enabled,
        staleTime: 0,
        placeholderData: keepPreviousData,
        queryFn: async ({ signal }) => {
            // Determine status filter based on selected tab and status filters
            let statusParam: string[] | string | undefined;
            let validationTypeParam: string[] | undefined;
            let threatScannerParam: string[] | undefined;
            let adminDecisionParam: string[] | undefined;
            let exactStatusParam: boolean | undefined;

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
                if (state.threatScannerFilters.size > 0) {
                    threatScannerParam = Array.from(state.threatScannerFilters);
                } else {
                    threatScannerParam = state.availableThreatScanners;
                }
                if (state.quarantineFilters.size > 0) {
                    adminDecisionParam = Array.from(state.quarantineFilters);
                }
            } else if (state.selectedTab === 2) {
                // Auto Rejected tab - show scans with validation failures
                if (state.validationTypeFilters.size > 0) {
                    validationTypeParam = Array.from(state.validationTypeFilters);
                } else {
                    validationTypeParam = state.availableValidationTypes;
                }
            } else if (state.selectedTab === 3) {
                // Errors tab - scans that errored and may need an admin decision
                statusParam = ['ERROR'];
                exactStatusParam = true;
                if (state.quarantineFilters.size > 0) {
                    adminDecisionParam = Array.from(state.quarantineFilters);
                }
            }

            const response = await service.admin.getAllScans(controllerFromSignal(signal), {
                size: state.pageSize,
                offset: state.currentPage * state.pageSize,
                publisher: state.publisherQuery || undefined,
                namespace: state.namespaceQuery || undefined,
                name: state.nameQuery || undefined,
                status: statusParam,
                validationType: validationTypeParam,
                threatScannerName: threatScannerParam,
                adminDecision: adminDecisionParam,
                exactStatus: exactStatusParam,
                dateStartedFrom: dateParams.dateStartedFrom,
                dateStartedTo: dateParams.dateStartedTo,
                enforcement: state.enforcement
            });

            return convertScans(response);
        }
    });

    useEffect(() => {
        // When there is nothing to fetch for the current tab, reflect an empty list.
        if (state.filterOptionsLoaded && (noQuarantineData || noAutoRejectedData)) {
            dispatch({ type: 'SET_SCANS', payload: { scans: [], totalScans: 0 } });
            dispatch({ type: 'SET_LOADING_SCANS', payload: false });
        }
    }, [state.filterOptionsLoaded, noQuarantineData, noAutoRejectedData, dispatch]);

    useEffect(() => {
        if (enabled) {
            dispatch({ type: 'SET_LOADING_SCANS', payload: isLoading });
        }
    }, [enabled, isLoading, dispatch]);

    useEffect(() => {
        if (data) {
            dispatch({ type: 'SET_SCANS', payload: data });
            // `keepPreviousData` makes the query's `isLoading` stay false across a key
            // change once any fetch has ever succeeded, so it never flips the loading
            // flag `SET_TAB` forced on back to false. Fresh data for the current key is
            // the reliable signal that the forced spinner can be cleared.
            dispatch({ type: 'SET_LOADING_SCANS', payload: false });
            dispatch({ type: 'SET_LAST_REFRESHED', payload: new Date() });
        }
    }, [data, dispatch]);

    useEffect(() => {
        if (error) {
            dispatch({ type: 'SET_LOADING_SCANS', payload: false });
            handleErrorRef.current(error);
        }
    }, [error, dispatch, handleErrorRef]);
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
    const dateParams = getDateRangeParams(state.dateRange);

    const { data, error } = useQuery({
        queryKey: [
            'admin',
            'scan',
            'scan-counts',
            state.selectedTab,
            Array.from(state.threatScannerFilters),
            Array.from(state.validationTypeFilters),
            state.availableThreatScanners,
            state.availableValidationTypes,
            state.dateRange,
            state.enforcement,
            state.refreshTrigger
        ],
        staleTime: 0,
        queryFn: async ({ signal }) => {
            // Determine filters based on selected tab (same logic as scans fetch)
            let validationTypeParam: string[] | undefined;
            let threatScannerParam: string[] | undefined;

            if (state.selectedTab === 1) {
                if (state.threatScannerFilters.size > 0) {
                    threatScannerParam = Array.from(state.threatScannerFilters);
                } else {
                    threatScannerParam = state.availableThreatScanners;
                }
            } else if (state.selectedTab === 2) {
                if (state.validationTypeFilters.size > 0) {
                    validationTypeParam = Array.from(state.validationTypeFilters);
                } else {
                    validationTypeParam = state.availableValidationTypes;
                }
            }

            return service.admin.getScanCounts(controllerFromSignal(signal), {
                ...dateParams,
                enforcement: state.enforcement,
                threatScannerName: threatScannerParam,
                validationType: validationTypeParam
            });
        }
    });

    useEffect(() => {
        if (data) {
            dispatch({ type: 'SET_SCAN_COUNTS', payload: data });
        }
    }, [data, dispatch]);

    useEffect(() => {
        if (error) {
            handleErrorRef.current(error);
        }
    }, [error, handleErrorRef]);
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
    const enabled = state.selectedTab >= 4;
    const dateParams = getFileDateRange(state.fileDateRange);

    const { data, error, isLoading } = useQuery({
        queryKey: [
            'admin',
            'scan',
            'files',
            state.selectedTab,
            state.currentPage,
            state.pageSize,
            state.publisherQuery,
            state.namespaceQuery,
            state.nameQuery,
            state.fileDateRange,
            state.refreshTrigger
        ],
        enabled,
        staleTime: 0,
        placeholderData: keepPreviousData,
        queryFn: async ({ signal }) => {
            const decisionParam = state.selectedTab === 4 ? 'allowed' : 'blocked';

            const response = await service.admin.getFiles(controllerFromSignal(signal), {
                size: state.pageSize,
                offset: state.currentPage * state.pageSize,
                publisher: state.publisherQuery || undefined,
                namespace: state.namespaceQuery || undefined,
                name: state.nameQuery || undefined,
                decision: decisionParam,
                dateDecidedFrom: dateParams.dateDecidedFrom,
                dateDecidedTo: dateParams.dateDecidedTo
            });

            return { files: response.files, totalFiles: response.totalSize };
        }
    });

    useEffect(() => {
        if (enabled) {
            dispatch({ type: 'SET_LOADING_FILES', payload: isLoading });
        }
    }, [enabled, isLoading, dispatch]);

    useEffect(() => {
        if (data) {
            dispatch({ type: 'SET_FILES', payload: data });
            // See the matching comment in useScansEffect: `keepPreviousData` keeps
            // `isLoading` false across a key change, so the loading flag `SET_TAB`
            // forces on can only be cleared reliably once fresh data lands.
            dispatch({ type: 'SET_LOADING_FILES', payload: false });
            dispatch({ type: 'SET_LAST_REFRESHED', payload: new Date() });
        }
    }, [data, dispatch]);

    useEffect(() => {
        if (error) {
            dispatch({ type: 'SET_LOADING_FILES', payload: false });
            handleErrorRef.current(error);
        }
    }, [error, dispatch, handleErrorRef]);
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
    const enabled = state.selectedTab >= 4;
    const dateParams = getFileDateRange(state.fileDateRange);

    const { data, error } = useQuery({
        queryKey: ['admin', 'scan', 'file-counts', state.fileDateRange, state.refreshTrigger],
        enabled,
        staleTime: 0,
        queryFn: ({ signal }) =>
            service.admin.getFileCounts(controllerFromSignal(signal), {
                dateDecidedFrom: dateParams.dateDecidedFrom,
                dateDecidedTo: dateParams.dateDecidedTo
            })
    });

    useEffect(() => {
        if (data) {
            dispatch({ type: 'SET_FILE_COUNTS', payload: data });
        }
    }, [data, dispatch]);

    useEffect(() => {
        if (error) {
            handleErrorRef.current(error);
        }
    }, [error, handleErrorRef]);
};

// ============================================================================
// Auto Refresh Effect
// ============================================================================

/**
 * Hook for periodic refresh - refreshes data every 30 seconds when enabled and page is visible
 */
export const useAutoRefreshEffect = (state: ScanState, dispatch: Dispatch<ScanAction>) => {
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
