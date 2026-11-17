/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
import { describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
import { FileDialog } from '../../../../src/components/scan-admin/dialogs';
import { ScanProvider, useScanContext } from '../../../../src/context/scan-admin';

type ScanContextValue = ReturnType<typeof useScanContext>;

describe('FileDialog', () => {
    it('deletes once and keeps the dialog open until the request completes', async () => {
        let resolveDelete!: () => void;
        const deleteFileDecisions = vi.fn(
            () =>
                new Promise<void>(resolve => {
                    resolveDelete = resolve;
                })
        );
        const file = { id: '1', fileName: 'payload.js', fileHash: 'abc', decision: 'allowed' };
        const service = {
            admin: {
                deleteFileDecisions,
                getAllScans: vi.fn().mockResolvedValue({ scans: [], totalSize: 0 }),
                getScanFilterOptions: vi.fn().mockResolvedValue({ validationTypes: [], threatScannerNames: [] }),
                getScanCounts: vi.fn().mockResolvedValue({}),
                getFiles: vi.fn().mockResolvedValue({ files: [file], totalSize: 1 }),
                getFileCounts: vi.fn().mockResolvedValue({ allowed: 1, blocked: 0, total: 1 })
            }
        };
        let context!: ScanContextValue;
        const Probe = () => {
            context = useScanContext();
            return null;
        };
        renderWithProviders(
            <ScanProvider service={service}>
                <Probe />
                <FileDialog />
            </ScanProvider>,
            { mainContext: { service: service as never } }
        );
        act(() => context.actions.setTab(4));
        await waitFor(() => expect(context.state.files).toHaveLength(1));
        act(() => context.actions.setFilesChecked(new Set(['1'])));
        act(() => context.actions.openFileDialog('delete'));

        const confirm = screen.getByRole('button', { name: 'Confirm Delete' }) as HTMLButtonElement;
        fireEvent.click(confirm);
        await waitFor(() => expect(confirm.disabled).toBe(true));
        fireEvent.click(confirm);
        const cancel = screen.getByRole('button', { name: 'Cancel' }) as HTMLButtonElement;
        expect(cancel.disabled).toBe(true);
        fireEvent.click(cancel);
        fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
        expect(context.state.fileDialogOpen).toBe(true);

        await act(async () => resolveDelete());
        await waitFor(() => expect(context.state.fileDialogOpen).toBe(false));
        expect(deleteFileDecisions).toHaveBeenCalledTimes(1);
        expect(context.state.filesChecked.size).toBe(0);
    });
});
