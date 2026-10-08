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
import { UserData } from '../../src/extension-registry-types';

const user = { createTokenUrl: 'https://registry.test/user/token/create' } as UserData;
const csrfToken = () => jsonResponse({ header: 'X-XSRF-TOKEN', value: 'token' });

describe('createAccessToken request', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('sends the namespace and extension scope when given', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ id: 1 }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.createAccessToken(new AbortController(), user, 'ci', { namespace: 'foo', extension: 'bar' });

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe(
            'https://registry.test/user/token/create?description=ci&namespace=foo&extension=bar'
        );
    });

    it('sends publishingOnly only when it is set', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ id: 1 }), csrfToken(), jsonResponse({ id: 2 }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.createAccessToken(new AbortController(), user, 'ci', { publishingOnly: true });
        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe(
            'https://registry.test/user/token/create?description=ci&publishingOnly=true'
        );

        await service.createAccessToken(new AbortController(), user, 'ci', { publishingOnly: false });
        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/user/token/create?description=ci');
    });

    it('sends no scope parameters for an unscoped token', async () => {
        const fetchMock = stubFetch(csrfToken(), jsonResponse({ id: 1 }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.createAccessToken(new AbortController(), user, 'ci');

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/user/token/create?description=ci');
    });
});
