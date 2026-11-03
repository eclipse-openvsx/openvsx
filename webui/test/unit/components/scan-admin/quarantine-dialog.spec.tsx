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
import { act, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
import { QuarantineDialog } from '../../../../src/components/scan-admin/dialogs';
import { ScanProvider, useScanContext } from '../../../../src/context/scan-admin';

type ScanContextValue = ReturnType<typeof useScanContext>;

function scanFixture(overrides: Record<string, unknown> = {}) {
    return {
        id: '1',
        namespace: 'acme',
        extensionName: 'widget',
        version: '1.0.0',
        status: 'ERROR',
        dateScanStarted: '2026-01-01',
        errorMessage: 'scanner crashed',
        threats: [],
        ...overrides
    };
}

/** A version quarantined at activation because another publisher's extension took its display name. */
function conflictFixture(overrides: Record<string, unknown> = {}) {
    return scanFixture({
        status: 'QUARANTINED',
        errorMessage: undefined,
        validationFailures: [
            {
                id: 'f1',
                type: 'DISPLAY_NAME_CONFLICT',
                ruleName: 'Exact display name match',
                reason: "Display name 'Widget' is already used by the extension 'other.widget'.",
                enforcedFlag: true
            }
        ],
        ...overrides
    });
}

/** Renders the dialog with `scans` loaded and the first one selected for allowing, or blocking. */
async function openAllowDialog(scans: ReturnType<typeof scanFixture>[], action: 'allow' | 'block' = 'allow') {
    const service = {
        admin: {
            getAllScans: vi.fn().mockResolvedValue({ scans, totalSize: scans.length }),
            getScanFilterOptions: vi.fn().mockResolvedValue({ validationTypes: [], threatScannerNames: [] }),
            getScanCounts: vi.fn().mockResolvedValue({}),
            getFiles: vi.fn().mockResolvedValue({ files: [], totalSize: 0 }),
            getFileCounts: vi.fn().mockResolvedValue({ allowed: 0, blocked: 0, total: 0 })
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
            <QuarantineDialog />
        </ScanProvider>,
        { mainContext: { service: service as never } }
    );

    await waitFor(() => expect(context.state.scans).toHaveLength(scans.length));
    act(() => context.actions.toggleQuarantinedCheck(scans[0].id, true));
    act(() => (action === 'allow' ? context.actions.openAllowDialog() : context.actions.openBlockDialog()));
}

describe('QuarantineDialog', () => {
    it('warns about activating without a completed scan for an errored scan without threats', async () => {
        await openAllowDialog([scanFixture()]);

        expect(await screen.findByText(/without a completed scan/)).toBeTruthy();
        expect(screen.getByText('acme.widget 1.0.0')).toBeTruthy();
        expect(screen.getByText('scanner crashed')).toBeTruthy();
    });

    it('shows the files to allow-list when an errored scan carries threats', async () => {
        const threat = { id: 't1', fileName: 'payload.js', fileHash: 'abc', type: 'ClamAV', enforcedFlag: true };
        await openAllowDialog([scanFixture({ threats: [threat] })]);

        expect(await screen.findByText(/Are you sure you want to allow 1 file/)).toBeTruthy();
        expect(screen.queryByText(/without a completed scan/)).toBeNull();
    });

    it('confirms the version, not files, and names the extension holding the display name', async () => {
        await openAllowDialog([conflictFixture()]);

        expect(await screen.findByText('Activate 1 extension version? It becomes publicly available.')).toBeTruthy();
        expect(screen.getByText(/already shows, so both will show the same name/)).toBeTruthy();
        expect(screen.getByText(/already used by the extension 'other.widget'/)).toBeTruthy();
        expect(screen.queryByText(/0 files/)).toBeNull();
    });

    it('warns about the display name conflict next to the files of a scan that also has threats', async () => {
        const threat = { id: 't1', fileName: 'payload.js', fileHash: 'abc', type: 'ClamAV', enforcedFlag: true };
        await openAllowDialog([conflictFixture({ threats: [threat] })]);

        expect(await screen.findByText(/Are you sure you want to allow 1 file/)).toBeTruthy();
        expect(screen.getByText(/already used by the extension 'other.widget'/)).toBeTruthy();
    });

    it('blocks a version a check quarantined without the display name warning', async () => {
        await openAllowDialog([conflictFixture()], 'block');

        expect(await screen.findByText('Block 1 extension version? It stays inactive.')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Confirm Block' })).toBeTruthy();
        expect(screen.queryByText(/already used by the extension/)).toBeNull();
    });
});
