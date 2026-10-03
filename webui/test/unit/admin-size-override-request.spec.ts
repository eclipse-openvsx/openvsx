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

// Every mutator fetches a CSRF token first, so the stub has to answer that call before the one
// under test; assertions therefore look at the last call, not the first.
const csrfToken = () => jsonResponse({ header: 'X-XSRF-TOKEN', value: 'token' });

describe('admin size override requests', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('lists overrides from the admin endpoint', async () => {
        const fetchMock = stubFetch(jsonResponse({ sizeOverrides: [] }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getSizeOverrides(new AbortController());

        expect(String(fetchMock.mock.calls[0][0])).toBe('https://registry.test/admin/size-overrides');
    });

    it('posts a new override to the create endpoint', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.createSizeOverride({ id: 0, namespace: 'foo', maxSize: 100 });

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/admin/size-overrides/create');
    });

    // The id has to reach the URL. Addressing the wrong row is silent: the request succeeds and
    // edits somebody else's override.
    it('puts an update to the override its id names', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.updateSizeOverride(42, { id: 42, namespace: 'foo', maxSize: 200 });

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/admin/size-overrides/42');
    });

    it('deletes the override its id names', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.deleteSizeOverride(42);

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/admin/size-overrides/42');
    });
});
