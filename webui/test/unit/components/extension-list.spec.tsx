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
import { renderWithProviders } from '../support/test-providers';
import { createTestViewport } from '../support/viewport';
import { ExtensionList } from '../../../src/components/extension-list';
import { ExtensionFilter, ExtensionRegistryService } from '../../../src/extension-registry-service';
import { SearchEntry, SearchResult } from '../../../src/extension-registry-types';

const PAGE_SIZE = 2;

// No icon file, so only the list's sentinel is observed by the test viewport.
const entry = (idx: number): SearchEntry =>
    ({
        namespace: 'foo',
        name: `ext${idx}`,
        displayName: `Extension ${idx}`,
        version: '1.0.0',
        targetPlatform: 'universal',
        files: {}
    }) as unknown as SearchEntry;

const page = (offset: number, totalSize: number): SearchResult => ({
    offset,
    totalSize,
    extensions: Array.from({ length: Math.min(PAGE_SIZE, totalSize - offset) }, (_, i) => entry(offset + i))
});

function renderList(search: (signal: AbortSignal, filter: ExtensionFilter) => Promise<SearchResult>) {
    const viewport = createTestViewport();
    const spy = vi.fn(search);
    const handleError = vi.fn();
    const filter: ExtensionFilter = {
        query: 'q',
        category: '',
        offset: 0,
        size: PAGE_SIZE,
        sortBy: 'relevance',
        sortOrder: 'desc'
    };
    renderWithProviders(<ExtensionList filter={filter} onUpdate={() => {}} />, {
        mainContext: { service: { search: spy } as unknown as ExtensionRegistryService, handleError },
        viewport: viewport.observer
    });
    return { search: spy, viewport, handleError };
}

const offsets = (search: ReturnType<typeof vi.fn>) => search.mock.calls.map(([, filter]) => filter.offset);

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

// Fails the first `times` requests for the page at `offset`. A failure lands after a delay, as over
// a network: an instant one batches `isFetching` back to false within one render, hiding a retry loop.
const failingPage = (offset: number, times: number, totalSize = 4) => {
    let failures = 0;
    return async (_: AbortSignal, filter: ExtensionFilter): Promise<SearchResult> => {
        if (filter.offset === offset && failures < times) {
            failures++;
            await delay(20);
            throw new Error('load failed');
        }
        return page(filter.offset!, totalSize);
    };
};

describe('ExtensionList', () => {
    it('loads the next page only once the end of the list comes near the viewport', async () => {
        const { search, viewport } = renderList(async (_, filter) => page(filter.offset!, 4));
        expect(await screen.findByText('Extension 1')).toBeInTheDocument();
        expect(offsets(search)).toEqual([0]);
        expect(viewport.observed()).toEqual([expect.objectContaining({ rootMargin: '200px' })]);

        viewport.setInView(true);

        expect(await screen.findByText('Extension 3')).toBeInTheDocument();
        expect(offsets(search)).toEqual([0, 2]);
    });

    it('keeps loading pages while the end of the list stays in view, and stops at the last one', async () => {
        const { search, viewport } = renderList(async (_, filter) => page(filter.offset!, 5));
        await screen.findByText('Extension 1');

        viewport.setInView(true);

        expect(await screen.findByText('Extension 4')).toBeInTheDocument();
        expect(offsets(search)).toEqual([0, 2, 4]);
        expect(viewport.observed()).toHaveLength(0);
    });

    it('does not request another page while one is loading', async () => {
        let release: () => void = () => {};
        const { search, viewport } = renderList((_, filter) =>
            filter.offset === 0
                ? Promise.resolve(page(0, 4))
                : new Promise(resolve => (release = () => resolve(page(filter.offset!, 4))))
        );
        await screen.findByText('Extension 1');

        viewport.setInView(true);
        await waitFor(() => expect(offsets(search)).toEqual([0, 2]));
        viewport.setInView(false);
        viewport.setInView(true);
        expect(offsets(search)).toEqual([0, 2]);

        release();
        expect(await screen.findByText('Extension 3')).toBeInTheDocument();
        expect(offsets(search)).toEqual([0, 2]);
    });

    it('waits for Retry after a failed page instead of requesting it again', async () => {
        const { search, viewport, handleError } = renderList(failingPage(2, 2));
        await screen.findByText('Extension 1');
        viewport.setInView(true);
        await waitFor(() => expect(offsets(search)).toEqual([0, 2]));
        // The loading page's skeletons push the end of the list out of view, and clearing them on
        // failure brings it back: that must not count as the user scrolling back to it.
        viewport.setInView(false);
        const retry = await screen.findByText('Retry');
        viewport.setInView(true);
        await delay(200);
        expect(offsets(search)).toEqual([0, 2]);
        expect(screen.getByText(/couldn.t load more extensions/)).toBeInTheDocument();

        await userEvent.click(retry);
        await delay(200);
        expect(offsets(search)).toEqual([0, 2, 2]);
        expect(screen.getByText('Retry')).toBeInTheDocument();
        expect(handleError).not.toHaveBeenCalled();
    });

    it('loads a failed page on Retry and keeps loading from there', async () => {
        const { search, viewport } = renderList(failingPage(2, 1, 6));
        await screen.findByText('Extension 1');
        viewport.setInView(true);

        await userEvent.click(await screen.findByText('Retry'));

        expect(await screen.findByText('Extension 5')).toBeInTheDocument();
        expect(screen.queryByText(/couldn.t load more extensions/)).not.toBeInTheDocument();
        expect(offsets(search)).toEqual([0, 2, 2, 4]);
    });

    it('shows a failed first page inline and loads it on Retry', async () => {
        const { search, handleError } = renderList(failingPage(0, 1));

        await userEvent.click(await screen.findByText('Retry'));

        expect(await screen.findByText('Extension 1')).toBeInTheDocument();
        expect(screen.queryByText(/couldn.t load extensions/)).not.toBeInTheDocument();
        expect(offsets(search)).toEqual([0, 0]);
        expect(handleError).not.toHaveBeenCalled();
    });

    it('requests nothing more when the first page holds every result', async () => {
        const { search, viewport } = renderList(async () => page(0, 2));
        await screen.findByText('Extension 1');

        viewport.setInView(true);

        expect(offsets(search)).toEqual([0]);
        expect(viewport.observed()).toHaveLength(0);
    });
});
