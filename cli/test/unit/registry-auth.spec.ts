/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, it, expect, afterEach } from 'vitest';
import * as http from 'node:http';
import { AddressInfo } from 'node:net';
import { Registry } from '../../src/registry';

describe('Registry token headers', () => {

    const servers: http.Server[] = [];

    afterEach(async () => {
        for (const server of servers.splice(0)) {
            await new Promise<void>(resolve => server.close(() => resolve()));
        }
    });

    async function serve(handler: http.RequestListener): Promise<string> {
        const server = http.createServer(handler);
        servers.push(server);
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    }

    it('sends the personal access token as Authorization: Bearer by default', async () => {
        const headers: http.IncomingHttpHeaders[] = [];
        const url = await serve((req, res) => {
            headers.push(req.headers);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: 'Valid token' }));
        });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(headers[0].authorization).toBe('Bearer the.pat');
        expect(headers[0]['x-openvsx-token']).toBeUndefined();
    });

    // Authorization is already claimed by Basic auth to a fronting reverse proxy in this
    // configuration, so the token has to fall back to the X-OpenVSX-Token header instead of
    // clobbering (or being clobbered by) the proxy credentials.
    it('falls back to the X-OpenVSX-Token header when username/password are configured', async () => {
        const headers: http.IncomingHttpHeaders[] = [];
        const url = await serve((req, res) => {
            headers.push(req.headers);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: 'Valid token' }));
        });
        const registry = new Registry({ registryUrl: url, username: 'proxy-user', password: 'proxy-pass' });

        await registry.verifyPat('foo', 'the.pat');

        expect(headers[0]['x-openvsx-token']).toBe('the.pat');
        expect(headers[0].authorization).toBe(`Basic ${Buffer.from('proxy-user:proxy-pass').toString('base64')}`);
    });

    it('still sends the token query parameter alongside the header, for older registries', async () => {
        const requests: URL[] = [];
        const url = await serve((req, res) => {
            requests.push(new URL(req.url ?? '/', 'http://127.0.0.1'));
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: 'Valid token' }));
        });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].searchParams.get('token')).toBe('the.pat');
    });
});
