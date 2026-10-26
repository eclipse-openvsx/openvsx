/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from './support/fetch';
import { sendRequest } from '../../src/server-request';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('sendRequest', () => {
    it('resolves with the parsed body of a successful response', async () => {
        stubFetch(jsonResponse({ name: 'redhat' }));

        await expect(sendRequest({ endpoint: 'https://open-vsx.org/api/redhat' })).resolves.toEqual({
            name: 'redhat'
        });
    });

    it('rejects with the error result and its status when the server answers 200 with an error body', async () => {
        stubFetch(jsonResponse({ error: 'Namespace not found' }));

        await expect(sendRequest({ endpoint: 'https://open-vsx.org/api/nope' })).rejects.toEqual({
            error: 'Namespace not found',
            status: 200
        });
    });

    // The registry aggregates per-version outcomes when deleting extension versions, so a fully
    // successful delete answers 200 with an empty error next to the success message.
    it('resolves a result whose success is reported alongside an empty error', async () => {
        stubFetch(jsonResponse({ success: 'Deleted 2 versions', error: '' }));

        await expect(
            sendRequest({ endpoint: 'https://open-vsx.org/user/extension/foo/bar/delete', method: 'POST' })
        ).resolves.toEqual({ success: 'Deleted 2 versions', error: '' });
    });

    it('rejects with the error response of a failed request', async () => {
        stubFetch(jsonResponse({ error: 'Forbidden', message: 'no access' }, 403));

        await expect(sendRequest({ endpoint: 'https://open-vsx.org/admin/namespace/x' })).rejects.toMatchObject({
            error: 'Forbidden',
            status: 403
        });
    });

    it('sends a request once, leaving retries to the query client', async () => {
        const fetchMock = stubFetch(jsonResponse({ error: 'boom' }, 500));

        await expect(sendRequest({ endpoint: 'https://open-vsx.org/api/-/search' })).rejects.toMatchObject({
            status: 500
        });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('rejects a 429 with the wait the registry asks for', async () => {
        const fetchMock = stubFetch(
            new Response(null, { status: 429, headers: { 'X-Rate-Limit-Retry-After-Seconds': '7' } }),
            new Response(null, { status: 429, headers: { 'Retry-After': '3' } })
        );

        await expect(sendRequest({ endpoint: 'https://open-vsx.org/api/-/search' })).rejects.toMatchObject({
            status: 429,
            retryAfter: 7
        });
        await expect(sendRequest({ endpoint: 'https://open-vsx.org/api/-/search' })).rejects.toMatchObject({
            status: 429,
            retryAfter: 3
        });
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('passes the signal on to fetch', async () => {
        const fetchMock = stubFetch(jsonResponse({}));
        const signal = new AbortController().signal;

        await sendRequest({ endpoint: 'https://open-vsx.org/api/-/search', signal });

        expect(fetchMock.mock.calls[0][1]).toMatchObject({ signal });
    });
});
