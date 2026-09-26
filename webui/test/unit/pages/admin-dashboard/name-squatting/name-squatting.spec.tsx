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
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../../support/test-providers';
import { NameSquatting } from '../../../../../src/pages/admin-dashboard/name-squatting/name-squatting';
import { AdminService, ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import type { NameSquattingActionResponse, NameSquattingFlag } from '../../../../../src/extension-registry-types';
import { counts, flag } from '../../../support/name-squatting-data';

interface AdminStubs {
    flags?: NameSquattingFlag[];
    clearResponse?: NameSquattingActionResponse;
}

function stubService({ flags = [flag()], clearResponse }: AdminStubs = {}) {
    const admin = {
        getNameSquattingFlags: vi.fn().mockResolvedValue({ offset: 0, totalSize: flags.length, flags }),
        getNameSquattingCounts: vi.fn().mockResolvedValue(counts()),
        clearNameSquattingFlags: vi.fn().mockResolvedValue(
            clearResponse ?? {
                processed: 1,
                successful: 1,
                failed: 0,
                results: [{ namespace: 'squatter', extension: 'squatty-theme', success: true }]
            }
        ),
        deleteNameSquattingExtensions: vi.fn().mockResolvedValue({
            processed: 1,
            successful: 1,
            failed: 0,
            results: [{ namespace: 'squatter', extension: 'squatty-theme', success: true }]
        })
    } as unknown as AdminService;

    return { service: { serverUrl: 'https://open-vsx.org', admin } as ExtensionRegistryService, admin };
}

describe('NameSquatting', () => {
    it('lists the flagged extensions returned for the first page', async () => {
        const { service, admin } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });

        expect(await screen.findByText('Squatty Theme')).toBeInTheDocument();
        expect(admin.getNameSquattingFlags).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ offset: 0, size: 10 })
        );
    });

    it('shows the per-state counts on the state filters', async () => {
        const { service } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });

        expect(await screen.findByRole('button', { name: /published \(1\)/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /publication blocked \(0\)/i })).toBeInTheDocument();
    });

    it('narrows the query to the selected state', async () => {
        const { service, admin } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });
        await screen.findByText('Squatty Theme');

        await userEvent.click(screen.getByRole('button', { name: /publication blocked/i }));

        await waitFor(() =>
            expect(admin.getNameSquattingFlags).toHaveBeenCalledWith(
                expect.anything(),
                expect.objectContaining({ state: ['REJECTED'] })
            )
        );
    });

    it('passes the typed search terms to the query once they settle', async () => {
        const { service, admin } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });
        await screen.findByText('Squatty Theme');

        await userEvent.type(screen.getByLabelText('Namespace'), 'squat');

        await waitFor(() =>
            expect(admin.getNameSquattingFlags).toHaveBeenCalledWith(
                expect.anything(),
                expect.objectContaining({ namespace: 'squat' })
            )
        );
    });

    it('clears the findings for the confirmed extension', async () => {
        const { service, admin } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });
        await screen.findByText('Squatty Theme');

        await userEvent.click(screen.getByRole('button', { name: /mark as false positive/i }));
        await userEvent.click(screen.getByRole('button', { name: /clear findings/i }));

        await waitFor(() =>
            expect(admin.clearNameSquattingFlags).toHaveBeenCalledWith({
                targets: [{ namespace: 'squatter', extension: 'squatty-theme' }]
            })
        );
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('soft-deletes the confirmed extension', async () => {
        const { service, admin } = stubService();
        renderWithProviders(<NameSquatting />, { mainContext: { service } });
        await screen.findByText('Squatty Theme');

        await userEvent.click(screen.getByRole('button', { name: /soft delete extension/i }));
        await userEvent.click(screen.getByRole('button', { name: /^soft delete$/i }));

        await waitFor(() =>
            expect(admin.deleteNameSquattingExtensions).toHaveBeenCalledWith({
                targets: [{ namespace: 'squatter', extension: 'squatty-theme' }]
            })
        );
    });

    // The endpoint answers 200 with per-extension outcomes, so a rejected target has to reach the
    // dialog rather than being reported as a success.
    it('keeps the dialog open and reports why a moderation action was refused', async () => {
        const { service } = stubService({
            clearResponse: {
                processed: 1,
                successful: 0,
                failed: 1,
                results: [
                    {
                        namespace: 'squatter',
                        extension: 'squatty-theme',
                        success: false,
                        error: 'No name squatting findings are recorded for this extension'
                    }
                ]
            }
        });
        renderWithProviders(<NameSquatting />, { mainContext: { service } });
        await screen.findByText('Squatty Theme');

        await userEvent.click(screen.getByRole('button', { name: /mark as false positive/i }));
        await userEvent.click(screen.getByRole('button', { name: /clear findings/i }));

        expect(
            await screen.findByText(/no name squatting findings are recorded for this extension/i)
        ).toBeInTheDocument();
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('tells the administrator when nothing is flagged', async () => {
        const { service } = stubService({ flags: [] });
        renderWithProviders(<NameSquatting />, { mainContext: { service } });

        expect(await screen.findByText(/no extensions are flagged for name squatting/i)).toBeInTheDocument();
    });
});
