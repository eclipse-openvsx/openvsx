/******************************************************************************
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
 *****************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import type {
    NameSquattingNamespace,
    NameSquattingNamespacePair,
    NameSquattingNamespacePairs
} from '../../../../../src/extension-registry-types';
import { NamespacesTab } from '../../../../../src/pages/admin-dashboard/name-squatting/namespaces-tab';
import { renderWithProviders } from '../../../support/test-providers';

const namespace = (name: string, downloads: number): NameSquattingNamespace => ({
    name,
    downloads,
    activeExtensions: 1,
    firstPublished: '2024-01-01T00:00:00Z'
});

const pair = (lookalike: string, overrides: Partial<NameSquattingNamespacePair> = {}): NameSquattingNamespacePair => ({
    target: namespace('redhat', 50_000),
    lookalike: namespace(lookalike, 10),
    editDistance: 1,
    similarity: 0.5,
    separatorOnly: false,
    sharedMember: false,
    popular: true,
    ...overrides
});

const page = (
    pairs: NameSquattingNamespacePair[],
    { offset = 0, hasMore = false, total = pairs.length, popularTotal = pairs.length } = {}
): NameSquattingNamespacePairs => ({ pairs, total, popularTotal, offset, hasMore });

// not named render*, so the testing-library naming rule does not treat the stub it returns as a render result
const mountTab = (admin: Record<string, unknown>, route = '/?tab=namespaces') => {
    renderWithProviders(<NamespacesTab />, {
        route,
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('NamespacesTab', () => {
    it('lists each pair with its target and look-alike, counting what the popular filter hides', async () => {
        const admin = mountTab({
            getNameSquattingNamespacePairs: vi
                .fn()
                .mockResolvedValue(page([pair('redhaat', { sharedMember: true })], { total: 7, popularTotal: 1 }))
        });

        await screen.findByText('redhaat');
        const [target, lookalike, comparison] = within(screen.getAllByRole('row')[1]).getAllByRole('cell');
        expect(within(target).getByText('redhat')).toBeInTheDocument();
        expect(within(lookalike).getByText('redhaat')).toBeInTheDocument();
        expect(within(comparison).getByText('1 edit')).toBeInTheDocument();
        expect(within(comparison).getByText('Shared member')).toBeInTheDocument();
        expect(screen.getByText(/showing 1 of 1 pair with a popular target · 7 in all/i)).toBeInTheDocument();
        expect(admin.getNameSquattingNamespacePairs).toHaveBeenCalledWith(expect.anything(), true, 'downloads', 0, 25);
    });

    // Turning the filter off is how quiet, long-term squatting on niche namespaces gets reviewed.
    it('asks for every pair once popular targets only is turned off', async () => {
        const user = userEvent.setup();
        const admin = mountTab({ getNameSquattingNamespacePairs: vi.fn().mockResolvedValue(page([pair('redhaat')])) });
        await screen.findByText('redhaat');

        await user.click(screen.getByLabelText('Popular targets only'));

        await waitFor(() =>
            expect(admin.getNameSquattingNamespacePairs).toHaveBeenLastCalledWith(
                expect.anything(),
                false,
                'downloads',
                0,
                25
            )
        );
    });

    it('asks for the newest look-alikes first when sorted that way', async () => {
        const user = userEvent.setup();
        const admin = mountTab({ getNameSquattingNamespacePairs: vi.fn().mockResolvedValue(page([pair('redhaat')])) });
        await screen.findByText('redhaat');

        await user.click(screen.getByRole('combobox', { name: 'Sort' }));
        await user.click(screen.getByRole('option', { name: 'Newest look-alike first' }));

        await waitFor(() =>
            expect(admin.getNameSquattingNamespacePairs).toHaveBeenLastCalledWith(
                expect.anything(),
                true,
                'newest',
                0,
                25
            )
        );
    });

    it('opens the view its link names', async () => {
        const admin = mountTab(
            { getNameSquattingNamespacePairs: vi.fn().mockResolvedValue(page([pair('redhaat')])) },
            '/?tab=namespaces&targets=all&sort=newest'
        );

        await screen.findByText('redhaat');
        expect(admin.getNameSquattingNamespacePairs).toHaveBeenCalledWith(expect.anything(), false, 'newest', 0, 25);
        expect(screen.getByLabelText('Popular targets only')).not.toBeChecked();
    });

    it('points to the full list when no pair has a popular target', async () => {
        mountTab({
            getNameSquattingNamespacePairs: vi.fn().mockResolvedValue(page([], { total: 3, popularTotal: 0 }))
        });

        expect(await screen.findByText('No pairs with a popular target')).toBeInTheDocument();
        expect(screen.getByText(/to see the other 3 pairs/i)).toBeInTheDocument();
    });

    it('loads the next page from the offset after the pairs it has', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingNamespacePairs: vi
                .fn()
                .mockResolvedValueOnce(page([pair('redhaat'), pair('red-hat')], { hasMore: true, total: 3 }))
                .mockResolvedValueOnce(page([pair('redhet')], { offset: 2, total: 3 }))
        });

        await user.click(await screen.findByRole('button', { name: 'Load more pairs' }));

        expect(await screen.findByText('redhet')).toBeInTheDocument();
        expect(admin.getNameSquattingNamespacePairs).toHaveBeenLastCalledWith(
            expect.anything(),
            true,
            'downloads',
            2,
            25
        );
    });

    it('explains a lookup stopped by the statement timeout', async () => {
        mountTab({ getNameSquattingNamespacePairs: vi.fn().mockRejectedValue({ error: 'timed out', status: 503 }) });

        expect(await screen.findByText(/the check took too long and was stopped/i)).toBeInTheDocument();
    });

    // The server stores the look-alike first; the allowlist reads the pair that way round.
    it('allows the look-alike alongside the target, with the reason given, and refreshes the pairs', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingNamespacePairs: vi.fn().mockResolvedValue(page([pair('redhaat')])),
            allowNameSquattingNamespaces: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Allow…' }));
        await user.type(screen.getByLabelText(/reason/i), '  same organization  ');
        await user.click(screen.getByRole('button', { name: 'Allow pair' }));

        await waitFor(() =>
            expect(admin.allowNameSquattingNamespaces).toHaveBeenCalledWith({
                namespace: 'redhaat',
                otherNamespace: 'redhat',
                reason: 'same organization'
            })
        );
        await waitFor(() => expect(admin.getNameSquattingNamespacePairs).toHaveBeenCalledTimes(2));
    });
});
