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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from './support/fetch';
import { ExtensionRegistryService } from '../../src/extension-registry-service';

// Every mutator fetches a CSRF token first, so assertions look at the last call.
const csrfToken = () => jsonResponse({ header: 'X-XSRF-TOKEN', value: 'token' });

describe('admin name squatting requests', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('asks for the collisions batch at the offset it was given', async () => {
        const fetchMock = stubFetch(jsonResponse({ groups: [], nextOffset: 0, hasMore: false }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getNameSquattingCollisions(new AbortController().signal, 50, 25);

        expect(String(fetchMock.mock.calls[0][0])).toBe(
            'https://registry.test/admin/name-squatting/collisions?offset=50&size=25'
        );
    });

    it('asks for the allowlist page it was given', async () => {
        const fetchMock = stubFetch(jsonResponse({ groups: [], page: 2, hasMore: false }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getNameSquattingAllowlist(new AbortController().signal, 2, 25);

        expect(String(fetchMock.mock.calls[0][0])).toBe(
            'https://registry.test/admin/name-squatting/allowlist?page=2&size=25'
        );
    });

    it('posts an allowed pair to the allowlist', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');
        const request = { namespace: 'b', extension: 'x', otherNamespace: 'a', otherExtension: 'x' };

        await service.admin.allowNameSquattingPair(request);

        const [url, init] = fetchMock.mock.calls.at(-1)!;
        expect(String(url)).toBe('https://registry.test/admin/name-squatting/allowlist');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual(request);
    });

    // Removing the wrong id silently reports somebody else's pair again.
    it('deletes the allow its id names', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.removeNameSquattingAllow(42);

        const [url, init] = fetchMock.mock.calls.at(-1)!;
        expect(String(url)).toBe('https://registry.test/admin/name-squatting/allowlist/42');
        expect(init.method).toBe('DELETE');
    });

    it('asks for the namespace pairs of the view and page it was given', async () => {
        const fetchMock = stubFetch(jsonResponse({ pairs: [], total: 0, popularTotal: 0, offset: 25, hasMore: false }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getNameSquattingNamespacePairs(new AbortController().signal, false, 'newest', 25, 25);

        expect(String(fetchMock.mock.calls[0][0])).toBe(
            'https://registry.test/admin/name-squatting/namespaces?popularOnly=false&sort=newest&offset=25&size=25'
        );
    });

    it('asks for the namespace allowlist page it was given', async () => {
        const fetchMock = stubFetch(jsonResponse({ allows: [], page: 1, hasMore: false }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getNameSquattingNamespaceAllowlist(new AbortController().signal, 1, 25);

        expect(String(fetchMock.mock.calls[0][0])).toBe(
            'https://registry.test/admin/name-squatting/namespaces/allowlist?page=1&size=25'
        );
    });

    it('posts an allowed namespace pair to the namespace allowlist', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');
        const request = { namespace: 'redhaat', otherNamespace: 'redhat', reason: 'Same organization' };

        await service.admin.allowNameSquattingNamespaces(request);

        const [url, init] = fetchMock.mock.calls.at(-1)!;
        expect(String(url)).toBe('https://registry.test/admin/name-squatting/namespaces/allowlist');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual(request);
    });
});
