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
import userEvent from '@testing-library/user-event';
import { createTestQueryClient, renderWithProviders } from '../../support/test-providers';
import { CuratedSections } from '../../../../src/pages/home/curated-sections';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { SearchEntry, SearchResult } from '../../../../src/extension-registry-types';

const entry = {
    namespace: 'foo',
    name: 'bar',
    displayName: 'Foo Bar',
    version: '1.0.0',
    files: {}
} as unknown as SearchEntry;

const result = (extensions: SearchEntry[]): SearchResult => ({ offset: 0, totalSize: extensions.length, extensions });

function renderSections(search: ExtensionRegistryService['search'], queryClient = createTestQueryClient()) {
    renderWithProviders(<CuratedSections sections={[{ title: 'Most downloaded', sortBy: 'downloadCount' }]} />, {
        queryClient,
        mainContext: { service: { search } as unknown as ExtensionRegistryService }
    });
}

describe('CuratedSections', () => {
    it('shows a row that failed to load with a retry that loads it again', async () => {
        const search = vi
            .fn()
            .mockRejectedValueOnce(new Error('load failed'))
            .mockResolvedValue(result([entry]));
        renderSections(search);

        expect(await screen.findByText('We couldn’t load extensions at this time.')).toBeInTheDocument();
        expect(screen.getByText('Most downloaded')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

        expect(await screen.findByText('Foo Bar')).toBeInTheDocument();
        expect(screen.queryByText('We couldn’t load extensions at this time.')).not.toBeInTheDocument();
        expect(search).toHaveBeenCalledTimes(2);
    });

    it('shows the row loading, not failed, while its retry is in flight', async () => {
        let resolveRetry: (value: SearchResult) => void = () => {};
        const search = vi
            .fn()
            .mockRejectedValueOnce(new Error('load failed'))
            .mockReturnValueOnce(new Promise<SearchResult>(resolve => (resolveRetry = resolve)));
        renderSections(search);

        await userEvent.click(await screen.findByRole('button', { name: 'Retry' }));

        // Still shown, but neither failed (no notice or stand-ins) nor loaded (no cards): skeleton slots.
        expect(screen.getByText('Most downloaded')).toBeInTheDocument();
        expect(screen.queryByText('We couldn’t load extensions at this time.')).not.toBeInTheDocument();
        expect(screen.queryByText('Language Support')).not.toBeInTheDocument();
        expect(screen.queryAllByRole('link', { hidden: true })).toHaveLength(0);

        await act(async () => resolveRetry(result([entry])));
        expect(await screen.findByText('Foo Bar')).toBeInTheDocument();
    });

    it('keeps the extensions a row already had when a refetch fails', async () => {
        const queryClient = createTestQueryClient();
        const search = vi
            .fn()
            .mockResolvedValueOnce(result([entry]))
            .mockRejectedValueOnce(new Error('load failed'));
        renderSections(search, queryClient);
        expect(await screen.findByText('Foo Bar')).toBeInTheDocument();

        // The query client notifies observers on a later tick; flush it so the row renders its error.
        await act(async () => {
            await queryClient.refetchQueries({ queryKey: ['home-curated'] });
            await new Promise(resolve => setTimeout(resolve, 0));
        });

        expect(queryClient.getQueryState(['home-curated', 'downloadCount'])?.status).toBe('error');
        expect(screen.getByText('Foo Bar')).toBeInTheDocument();
        expect(screen.queryByText('We couldn’t load extensions at this time.')).not.toBeInTheDocument();
        expect(search).toHaveBeenCalledTimes(2);
    });

    it('leaves out a row that loaded empty', async () => {
        const search = vi.fn().mockResolvedValue(result([]));
        renderSections(search);

        // The row shows while it loads, then goes once it comes back empty.
        await waitFor(() => expect(screen.queryByText('Most downloaded')).not.toBeInTheDocument());
        expect(screen.queryByText('We couldn’t load extensions at this time.')).not.toBeInTheDocument();
    });
});
