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

import type { Dispatch } from 'react';
import { useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { ScanState, ScanAction } from './scan-types';

// ============================================================================
// Confirm Action Hook
// ============================================================================

/**
 * Hook that returns the async confirm action handler for allow/block operations
 * on selected quarantined scans. Makes a single API call to persist the decision.
 * The backend handles adding enforced threat files to the allow/block list.
 */
export const useConfirmAction = (service: any, state: ScanState, dispatch: Dispatch<ScanAction>) => {
    const { mutate } = useReportedQuery(
        useMutation({
            mutationFn: async () => {
                // Get selected scan IDs
                const selectedScanIds: string[] = [];
                for (const id in state.quarantinedChecked) {
                    if (state.quarantinedChecked[id]) {
                        selectedScanIds.push(id);
                    }
                }

                if (selectedScanIds.length === 0) {
                    return { skipped: true };
                }

                const decision = state.confirmAction === 'allow' ? 'allowed' : 'blocked';

                // Backend handles adding enforced threat files to allow/block list automatically
                await service.admin.makeScanDecision({
                    scanIds: selectedScanIds,
                    decision
                });

                return { skipped: false };
            },
            onSuccess: result => {
                if (result.skipped) {
                    dispatch({ type: 'CLOSE_CONFIRM_DIALOG' });
                    return;
                }
                // Update local state and trigger refresh
                dispatch({ type: 'EXECUTE_CONFIRM_ACTION' });
                dispatch({ type: 'TRIGGER_REFRESH' });
            }
        }),
        { action: 'apply the scan decision' }
    );

    return useCallback(() => {
        mutate();
    }, [mutate]);
};

// ============================================================================
// Retry Scanner Jobs Action Hook
// ============================================================================

/**
 * Hook that returns the async action handler for retrying all failed scanner jobs
 * of a single terminal scan.
 */
export const useRetryFailedScannerJobsAction = (service: any, dispatch: Dispatch<ScanAction>) => {
    const { mutateAsync } = useReportedQuery(
        useMutation({
            mutationFn: (scanId: string) => service.admin.retryFailedScannerJobs(scanId),
            onSuccess: () => {
                dispatch({ type: 'TRIGGER_REFRESH' });
            }
        }),
        { action: 'retry the failed scanner jobs' }
    );

    return useCallback(
        async (scanId: string): Promise<void> => {
            try {
                await mutateAsync(scanId);
            } catch {
                // Reported by the hook.
            }
        },
        [mutateAsync]
    );
};

// ============================================================================
// File Action Hook
// ============================================================================

/**
 * Hook that returns the async file action handler for allow/block/delete operations
 * on selected files in Allowed/Blocked Files tabs.
 */
export const useFileAction = (service: any, state: ScanState, dispatch: Dispatch<ScanAction>) => {
    const { mutate } = useReportedQuery(
        useMutation({
            mutationFn: async () => {
                if (state.filesChecked.size === 0) {
                    return { skipped: true };
                }

                const selectedFileIds = Array.from(state.filesChecked).map(id => parseInt(id, 10));

                if (state.fileActionType === 'delete') {
                    await service.admin.deleteFileDecisions({
                        fileIds: selectedFileIds
                    });
                } else {
                    const decision = state.fileActionType === 'allow' ? 'allowed' : 'blocked';

                    const selectedFiles = state.files.filter(file => state.filesChecked.has(file.id));
                    const fileHashes = selectedFiles.map(file => file.fileHash);

                    await service.admin.makeFileDecision({
                        fileHashes,
                        decision
                    });
                }

                return { skipped: false };
            },
            onSuccess: result => {
                if (result.skipped) {
                    dispatch({ type: 'CLOSE_FILE_DIALOG' });
                    return;
                }
                // Update local state and trigger refresh
                dispatch({ type: 'SET_FILES_CHECKED', payload: new Set() });
                dispatch({ type: 'CLOSE_FILE_DIALOG' });
                dispatch({ type: 'RESET_PAGE' }); // Go back to first page after action
                dispatch({ type: 'TRIGGER_REFRESH' });
            }
        }),
        { action: 'update the file decisions' }
    );

    return useCallback(() => {
        mutate();
    }, [mutate]);
};
