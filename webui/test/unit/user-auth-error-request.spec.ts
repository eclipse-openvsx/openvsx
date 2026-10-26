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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from './support/fetch';
import { ExtensionRegistryService } from '../../src/extension-registry-service';

describe('getUserAuthError request', () => {
    afterEach(() => vi.unstubAllGlobals());

    // The server's ErrorJson carries `message` and `code` but no `error` field.
    it('rejects with the login error the registry answers with', async () => {
        stubFetch(jsonResponse({ message: 'Login failed', code: 'some-code' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await expect(service.getUserAuthError(new AbortController().signal)).rejects.toMatchObject({
            message: 'Login failed',
            code: 'some-code',
            status: 200
        });
    });
});
