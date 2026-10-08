/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, it, expect, afterEach } from 'vitest';
import * as http from 'node:http';
import { AddressInfo } from 'node:net';
import { request } from '../../src/request';

describe('request', () => {

    let server: http.Server | undefined;

    afterEach(async () => {
        await new Promise<void>(resolve => server ? server.close(() => resolve()) : resolve());
        server = undefined;
    });

    // fetch rejects a redirected streamed body with a cause whose message is empty; reporting that
    // cause would leave the user with a bare "Error: ".
    it('keeps the fetch error when its cause has no message', async () => {
        server = http.createServer((req, res) => {
            req.resume();
            res.writeHead(307, { Location: '/moved' });
            res.end();
        });
        await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', resolve));
        const url = new URL(`http://127.0.0.1:${(server.address() as AddressInfo).port}/upload`);
        async function* body() {
            yield new Uint8Array(10);
        }

        const err: Error = await request(url, { method: 'POST', body: body(), timeout: 0 }).then(() => new Error('resolved'), e => e);

        expect(err).toBeInstanceOf(TypeError);
        expect(err.message).toBe('fetch failed');
    });
});
