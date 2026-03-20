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

import { FC, createContext, useContext, useReducer, useMemo, useRef } from 'react';
import { initialScanState, ScanResult, FileDecision } from './scan-types';
import { scanReducer } from './scan-reducer';
import { ScanContextValue, ScanProviderProps, DerivedData } from './scan-context-types';
import {
    useFilterOptionsEffect,
    useScansEffect,
    useScanCountsEffect,
    useFilesEffect,
    useFileCountsEffect,
    useAutoRefreshEffect,
} from './scan-api-effects';
import { useConfirmAction, useFileAction } from './scan-api-actions';
import { useScanActions } from './scan-actions';

// ============================================================================
// Context Creation
// ============================================================================

const ScanContext = createContext<ScanContextValue | null>(null);

// ============================================================================
// Provider Component
// ============================================================================

export const ScanProvider: FC<ScanProviderProps> = ({ children, service, handleError }) => {
    const [state, dispatch] = useReducer(scanReducer, initialScanState);

    // Use a ref for handleError to avoid re-running effects when parent re-renders
    // This prevents infinite loops when errors trigger parent state changes
    const handleErrorRef = useRef(handleError);
    handleErrorRef.current = handleError;

    // ========================================================================
    // API Effects
    // ========================================================================

    useFilterOptionsEffect(service, state, dispatch, handleErrorRef);
    useScansEffect(service, state, dispatch, handleErrorRef);
    useScanCountsEffect(service, state, dispatch, handleErrorRef);
    useFilesEffect(service, state, dispatch, handleErrorRef);
    useFileCountsEffect(service, state, dispatch, handleErrorRef);
    useAutoRefreshEffect(state, dispatch);

    // ========================================================================
    // Async API Actions
    // ========================================================================

    const executeConfirmAction = useConfirmAction(service, state, dispatch, handleErrorRef);
    const executeFileAction = useFileAction(service, state, dispatch, handleErrorRef);

    // ========================================================================
    // Actions
    // ========================================================================

    const actions = useScanActions(dispatch, executeConfirmAction, executeFileAction);

    // ========================================================================
    // Derived Data (memoized)
    // ========================================================================

    const selectedExtensions = useMemo((): ScanResult[] => {
        return state.scans.filter(scan => state.quarantinedChecked[scan.id]);
    }, [state.scans, state.quarantinedChecked]);

    const selectedFiles = useMemo((): FileDecision[] => {
        return state.files.filter(file => state.filesChecked.has(file.id));
    }, [state.files, state.filesChecked]);

    const totalPages = useMemo(() => {
        // For tabs 0-2 (scans), use totalScans; for tabs 3-4 (files), use totalFiles
        const total = state.selectedTab >= 3 ? state.totalFiles : state.totalScans;
        return Math.ceil(total / state.pageSize);
    }, [state.selectedTab, state.totalScans, state.totalFiles, state.pageSize]);

    const derived: DerivedData = useMemo(() => ({
        selectedExtensions,
        selectedFiles,
        totalPages,
    }), [selectedExtensions, selectedFiles, totalPages]);

    // ========================================================================
    // Context Value
    // ========================================================================

    const contextValue = useMemo(() => ({
        state,
        dispatch,
        actions,
        derived,
    }), [state, actions, derived]);

    return (
        <ScanContext.Provider value={contextValue}>
            {children}
        </ScanContext.Provider>
    );
};

// ============================================================================
// Custom Hook
// ============================================================================

// eslint-disable-next-line react-refresh/only-export-components
export const useScanContext = (): ScanContextValue => {
    const context = useContext(ScanContext);
    if (!context) {
        throw new Error('useScanContext must be used within a ScanProvider');
    }
    return context;
};
