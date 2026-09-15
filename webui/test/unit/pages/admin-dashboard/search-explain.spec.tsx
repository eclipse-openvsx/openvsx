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

import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { SearchExplainAdmin } from '../../../../src/pages/admin-dashboard/search-explain/search-explain';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { SearchExplain, SearchIndex } from '../../../../src/extension-registry-types';

const explained: SearchExplain = {
    query: 'markdown',
    totalHits: 1024,
    references: { maxDownloadCount: 80771472, averageReviewRating: 0.12, oldestTimestamp: '2019-01-01T00:00Z' },
    entries: [
        {
            position: 0,
            namespace: 'yzhang',
            name: 'markdown-all-in-one',
            downloadCount: 640718,
            score: 1.9,
            textScore: 1.4,
            storedRelevance: 1.364,
            currentRelevance: 1.364,
            rating: 0.02,
            downloads: 0.008,
            recency: 0.656,
            unverified: true,
            unverifiedFactor: 0.5,
            deprecated: false,
            deprecatedFactor: 0.5,
            scoreDetail: {
                description: 'function score, product of:',
                value: 1.9,
                truncated: false,
                details: [
                    {
                        description: 'sum of:',
                        value: 1.4,
                        truncated: false,
                        details: [
                            {
                                description: 'weight(name:markdown in 42)',
                                value: 1.4,
                                truncated: false,
                                details: []
                            }
                        ]
                    },
                    {
                        description: 'field value function: relevance',
                        value: 1.364,
                        truncated: false,
                        details: []
                    }
                ]
            }
        }
    ]
};

// The page reads the active engine to decide whether it has anything to explain, so every render
// needs it stubbed; elasticsearch is the case the rest of these tests are about.
const withService = (
    explainSearch: (...args: never[]) => Promise<SearchExplain>,
    implementation: SearchIndex['implementation'] = 'elasticsearch'
) =>
    renderWithProviders(<SearchExplainAdmin />, {
        mainContext: {
            service: {
                admin: {
                    explainSearch,
                    getSearchIndex: () =>
                        Promise.resolve({
                            enabled: true,
                            implementation,
                            indexExists: implementation === 'elasticsearch',
                            activeExtensions: 1
                        } as SearchIndex)
                }
            } as unknown as ExtensionRegistryService
        }
    });

describe('SearchExplainAdmin', () => {
    // The page exists to answer "which half of the score put it there", so both halves have to be on it.
    it('shows the score split into its text and relevance halves', async () => {
        withService(() => Promise.resolve(explained));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));

        expect(await screen.findByText('yzhang.markdown-all-in-one')).toBeInTheDocument();
        expect(screen.getByText('1.900')).toBeInTheDocument();
        expect(screen.getByText('1.400')).toBeInTheDocument();
        expect(screen.getByText('1.364')).toBeInTheDocument();
    });

    /**
     * The whole point of the page: the score is a product of two halves that want different fixes, so the
     * arithmetic and the engine's account of the text half are both on it - the second is the part no
     * amount of arithmetic over the stored values can reconstruct.
     */
    it('expands a result into the arithmetic and the engine account behind it', async () => {
        withService(() => Promise.resolve(explained));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));
        expect(await screen.findByText('yzhang.markdown-all-in-one')).toBeInTheDocument();

        await userEvent.click(screen.getAllByLabelText('Show the score breakdown')[0]);

        expect(await screen.findByText(/score 1\.900 = text 1\.400 × relevance 1\.364/)).toBeInTheDocument();
        expect(screen.getByText(/rating 0\.020 \+ downloads 0\.008 \+ recency 0\.656/)).toBeInTheDocument();
        expect(screen.getByText('weight(name:markdown in 42)')).toBeInTheDocument();
    });

    // The penalty factors are configurable and both can apply, so the page reports what they cost rather
    // than asserting a number of its own - "then halved" was true of neither case reliably.
    it('reports what a penalty factor actually costs', async () => {
        withService(() => Promise.resolve(explained));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));
        expect(await screen.findByText('yzhang.markdown-all-in-one')).toBeInTheDocument();
        await userEvent.click(screen.getAllByLabelText('Show the score breakdown')[0]);

        expect(await screen.findByText('× 0.50 unverified')).toBeInTheDocument();
        expect(screen.queryByText(/halved/)).not.toBeInTheDocument();
    });

    // Each number belongs in one place. They were in a stacked bar and in three columns of their own,
    // which is two answers to the same question and one of them always the one you were not reading.
    it('does not repeat the relevance terms outside the breakdown', async () => {
        withService(() => Promise.resolve(explained));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));
        expect(await screen.findByText('yzhang.markdown-all-in-one')).toBeInTheDocument();

        expect(screen.queryByText('0.656')).not.toBeInTheDocument();
    });

    // The reference values belong to the registry rather than any extension, and are the usual reason a
    // term contributes nothing - so they are stated rather than left to be inferred from a column of zeros.
    it('states what the terms are measured against', async () => {
        withService(() => Promise.resolve(explained));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));

        expect(await screen.findByText('81M')).toBeInTheDocument();
        expect(screen.getByText('0.12')).toBeInTheDocument();
    });

    /**
     * The question is often about a result a long way down - the one that prompted this page sat at 767 -
     * so pages accumulate rather than replace, and positions stay the ones the search gave.
     */
    it('adds another page to the ones already shown', async () => {
        const page = (offset: number): SearchExplain => ({
            ...explained,
            entries: Array.from({ length: 25 }, (unused, index) => ({
                ...explained.entries[0],
                position: offset + index,
                namespace: 'ns',
                name: `ext-${offset + index}`
            }))
        });
        const explainSearch = vi.fn((abort: never, query: never, size: never, offset: number) =>
            Promise.resolve(page(offset))
        );
        withService(explainSearch as never);

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));
        expect(await screen.findByText('ns.ext-0')).toBeInTheDocument();
        expect(screen.queryByText('ns.ext-25')).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /show .* more/i }));

        // The first page is still there, and the second continues its numbering rather than restarting.
        expect(await screen.findByText('ns.ext-25')).toBeInTheDocument();
        expect(screen.getByText('ns.ext-0')).toBeInTheDocument();
        expect(explainSearch.mock.calls[1][3]).toBe(25);
    });

    // Nothing more to ask for when the results are all on screen.
    it('offers no further page once everything is shown', async () => {
        withService(() => Promise.resolve({ ...explained, totalHits: 1 }));

        await userEvent.type(screen.getByLabelText('Search term'), 'markdown');
        await userEvent.click(screen.getByRole('button', { name: /^explain$/i }));
        expect(await screen.findByText('yzhang.markdown-all-in-one')).toBeInTheDocument();

        expect(screen.queryByRole('button', { name: /show .* more/i })).not.toBeInTheDocument();
    });

    // An empty query matches everything, and a listing of everything in score order answers no question.
    it('does not search until a term is given', () => {
        const explainSearch = vi.fn(() => Promise.resolve(explained));
        withService(explainSearch);

        expect(explainSearch).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /^explain$/i })).toBeDisabled();
    });

    // Nothing about the page hints that it cannot work on a registry without elasticsearch, and the
    // request only happens on submit - so without this the admin types a query to find that out.
    it('says up front when the engine cannot be explained, and will not let a query be run', async () => {
        withService(() => Promise.reject(new Error('should not be called')), 'database');

        expect(await screen.findByText(/answered by/i)).toBeInTheDocument();
        expect(screen.getByText('database')).toBeInTheDocument();
        expect(screen.getByLabelText('Search term')).toBeDisabled();
        expect(screen.getByRole('button', { name: /^explain$/i })).toBeDisabled();
    });

    it('says nothing of the sort when elasticsearch is answering', async () => {
        withService(() => Promise.resolve(explained));

        await waitFor(() => expect(screen.getByLabelText('Search term')).toBeEnabled());
        expect(screen.queryByText(/answered by/i)).not.toBeInTheDocument();
    });
});
