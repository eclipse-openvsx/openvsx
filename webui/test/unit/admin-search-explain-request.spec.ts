/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 ********************************************************************************/

import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from './support/fetch';
import { ExtensionRegistryService } from '../../src/extension-registry-service';

const empty = {
    query: 'markdown',
    totalHits: 0,
    references: { maxDownloadCount: 1, averageReviewRating: 0 },
    entries: []
};

describe('admin.explainSearch', () => {
    afterEach(() => vi.unstubAllGlobals());

    /**
     * The arguments have to survive into the request.
     * <p>
     * They did not: the method took an offset and built a URL without it, so every page after the first
     * asked for the first again and came back numbered from one. The page-level test asserted the
     * argument reaching this method and the service-level behaviour was mocked out, so the bug lived in
     * the gap between the two - which is where this one sits.
     */
    it('asks for the page the caller asked for', async () => {
        const fetchMock = stubFetch(jsonResponse(empty));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.explainSearch(new AbortController(), 'markdown', 25, 50);

        const url = new URL(String(fetchMock.mock.calls[0][0]));
        expect(url.searchParams.get('query')).toBe('markdown');
        expect(url.searchParams.get('size')).toBe('25');
        expect(url.searchParams.get('offset')).toBe('50');
    });
});
