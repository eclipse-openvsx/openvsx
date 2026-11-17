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

import { useMemo } from 'react';
import { useScanContext } from '../../context/scan-admin';
import { ConfirmActionType, FileActionType, ScanResult, FileDecision } from '../../context/scan-admin';

/**
 * Return type for the useDialogs hook
 */
export interface UseDialogsReturn {
    /** Confirm dialog state and actions (for extension-level allow/block) */
    confirmDialog: {
        isOpen: boolean;
        action: ConfirmActionType;
        selectedExtensions: ScanResult[];
        isPending: boolean;
        close: () => void;
        execute: () => void;
    };
    /** File dialog state and actions (for file-level allow/block/delete) */
    fileDialog: {
        isOpen: boolean;
        actionType: FileActionType;
        selectedFiles: FileDecision[];
        isPending: boolean;
        close: () => void;
        execute: () => void;
    };
    openAllowDialog: () => void;
    openBlockDialog: () => void;
    openFileDialog: (action: FileActionType) => void;
}

/**
 * Hook for dialog state and actions.
 * Provides a clean interface for dialog components to consume.
 */
export const useDialogs = (): UseDialogsReturn => {
    const { state, actions, derived } = useScanContext();

    return useMemo(
        () => ({
            confirmDialog: {
                isOpen: state.confirmDialogOpen,
                action: state.confirmAction,
                selectedExtensions: derived.selectedExtensions,
                isPending: derived.isConfirmActionPending,
                close: actions.closeConfirmDialog,
                execute: actions.executeConfirmAction
            },
            fileDialog: {
                isOpen: state.fileDialogOpen,
                actionType: state.fileActionType,
                selectedFiles: derived.selectedFiles,
                isPending: derived.isFileActionPending,
                close: actions.closeFileDialog,
                execute: actions.executeFileAction
            },
            openAllowDialog: actions.openAllowDialog,
            openBlockDialog: actions.openBlockDialog,
            openFileDialog: actions.openFileDialog
        }),
        [
            state.confirmDialogOpen,
            state.confirmAction,
            state.fileDialogOpen,
            state.fileActionType,
            derived.selectedExtensions,
            derived.selectedFiles,
            derived.isConfirmActionPending,
            derived.isFileActionPending,
            actions.closeConfirmDialog,
            actions.executeConfirmAction,
            actions.closeFileDialog,
            actions.executeFileAction,
            actions.openAllowDialog,
            actions.openBlockDialog,
            actions.openFileDialog
        ]
    );
};
