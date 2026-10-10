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
import { act, renderHook, waitFor } from '@testing-library/react';
import { TestProviders } from '../../support/test-providers';
import { ScanProvider, useScanContext } from '../../../../src/context/scan-admin';

/** A promise plus its resolver, so a test can settle a mocked fetch on demand. */
function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>(res => {
        resolve = res;
    });
    return { promise, resolve };
}

function scanFixture(id: string, extensionName: string) {
    return { id, namespace: 'acme', extensionName, version: '1.0.0', status: 'PASSED', dateScanStarted: '2026-01-01' };
}

/**
 * `getAllScans` is deferred so each test controls exactly when a given request
 * settles; every other admin endpoint resolves immediately since only the scans
 * loading flag is under test here.
 */
function createMockService() {
    const scansCalls: Array<ReturnType<typeof deferred<{ scans: unknown[]; totalSize: number }>>> = [];
    const getAllScans = vi.fn(() => {
        const call = deferred<{ scans: unknown[]; totalSize: number }>();
        scansCalls.push(call);
        return call.promise;
    });

    const service = {
        admin: {
            getAllScans,
            getScanFilterOptions: vi
                .fn()
                .mockResolvedValue({ validationTypes: ['Malware'], threatScannerNames: ['ClamAV'] }),
            getScanCounts: vi.fn().mockResolvedValue({
                STARTED: 0,
                VALIDATING: 0,
                SCANNING: 0,
                PASSED: 0,
                QUARANTINED: 0,
                AUTO_REJECTED: 0,
                ERROR: 0,
                ALLOWED: 0,
                BLOCKED: 0,
                NEEDS_REVIEW: 0
            }),
            getFiles: vi.fn().mockResolvedValue({ files: [], totalSize: 0 }),
            getFileCounts: vi.fn().mockResolvedValue({ allowed: 0, blocked: 0, total: 0 })
        }
    };

    return { service, scansCalls, getAllScans };
}

function setup(service: unknown) {
    return renderHook(() => useScanContext(), {
        wrapper: ({ children }) => (
            <TestProviders mainContext={{ service: service as never }}>
                <ScanProvider service={service} handleError={() => {}}>
                    {children}
                </ScanProvider>
            </TestProviders>
        )
    });
}

describe('ScanProvider — scans loading flag', () => {
    // Regression test: switching tabs forces `isLoadingScans` back on (scan-reducer's
    // SET_TAB) to hide the outgoing tab's rows. `keepPreviousData` on the scans query
    // means its `isLoading` stays false across that key change once any fetch has
    // succeeded once, so the flag must be cleared when fresh data for the new tab
    // lands rather than by mirroring the query's `isLoading`.
    it('clears the spinner once the new tab data arrives, even though the query never reports isLoading again', async () => {
        const { service, scansCalls, getAllScans } = createMockService();
        const { result } = setup(service);

        await waitFor(() => expect(getAllScans).toHaveBeenCalledTimes(1));
        act(() => scansCalls[0].resolve({ scans: [scanFixture('scan-0', 'foo')], totalSize: 1 }));
        await waitFor(() => expect(result.current.state.scans).toHaveLength(1));
        expect(result.current.state.isLoadingScans).toBe(false);

        act(() => result.current.actions.setTab(1));
        expect(result.current.state.isLoadingScans).toBe(true);
        expect(result.current.state.scans).toHaveLength(0);

        await waitFor(() => expect(getAllScans).toHaveBeenCalledTimes(2));
        act(() => scansCalls[1].resolve({ scans: [scanFixture('scan-1', 'bar')], totalSize: 1 }));

        await waitFor(() => expect(result.current.state.scans).toHaveLength(1));
        expect(result.current.state.isLoadingScans).toBe(false);
    });
});

describe('ScanProvider — tab routing', () => {
    it('lists only errored scans on the Errors tab', async () => {
        const { service, scansCalls, getAllScans } = createMockService();
        const { result } = setup(service);

        await waitFor(() => expect(getAllScans).toHaveBeenCalledTimes(1));
        act(() => scansCalls[0].resolve({ scans: [], totalSize: 0 }));

        act(() => result.current.actions.setTab(3));
        await waitFor(() => expect(getAllScans).toHaveBeenCalledTimes(2));

        const params = (getAllScans.mock.calls[1] as unknown[])[1] as Record<string, unknown>;
        expect(params.status).toEqual(['ERROR']);
        expect(params.exactStatus).toBe(true);
        expect(params.enforcement).toBe('all');
    });

    it('serves file decisions from tab 4 (allowed) and 5 (blocked), not the scan list', async () => {
        const { service, getAllScans } = createMockService();
        const { result } = setup(service);
        await waitFor(() => expect(getAllScans).toHaveBeenCalledTimes(1));

        act(() => result.current.actions.setTab(4));
        await waitFor(() => expect(service.admin.getFiles).toHaveBeenCalledTimes(1));
        expect((service.admin.getFiles.mock.calls[0] as unknown[])[1]).toMatchObject({ decision: 'allowed' });

        act(() => result.current.actions.setTab(5));
        await waitFor(() => expect(service.admin.getFiles).toHaveBeenCalledTimes(2));
        expect((service.admin.getFiles.mock.calls[1] as unknown[])[1]).toMatchObject({ decision: 'blocked' });
        expect(getAllScans).toHaveBeenCalledTimes(1);
    });
});
