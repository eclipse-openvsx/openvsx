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

interface Request {
    url: URL;
    headers: http.IncomingHttpHeaders;
}

describe('Registry token headers', () => {

    const servers: http.Server[] = [];

    afterEach(async () => {
        for (const server of servers.splice(0)) {
            await new Promise<void>(resolve => server.close(() => resolve()));
        }
    });

    /**
     * Serves `/api/version` (a registry too old to know about header support, unless overridden) and
     * everything else with a canned success response, recording each such request.
     */
    async function serve(version: { status?: number; body?: unknown } = {}): Promise<{ url: string; requests: Request[] }> {
        const requests: Request[] = [];
        const versionStatus = version.status ?? 200;
        const versionBody = version.body ?? { version: '1.2.0' };
        const server = http.createServer((req, res) => {
            const url = new URL(req.url ?? '/', 'http://127.0.0.1');
            if (url.pathname === '/api/version') {
                res.writeHead(versionStatus, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(versionBody));
                return;
            }
            requests.push({ url, headers: req.headers });
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: 'Valid token' }));
        });
        servers.push(server);
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        return { url: `http://127.0.0.1:${(server.address() as AddressInfo).port}`, requests };
    }

    it('sends the personal access token as Authorization: Bearer by default', async () => {
        const { url, requests } = await serve();
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].headers.authorization).toBe('Bearer the.pat');
        expect(requests[0].headers['x-openvsx-token']).toBeUndefined();
    });

    // Authorization is already claimed by Basic auth to a fronting reverse proxy in this
    // configuration, so the token has to fall back to the X-OpenVSX-Token header instead of
    // clobbering (or being clobbered by) the proxy credentials.
    it('falls back to the X-OpenVSX-Token header when username/password are configured', async () => {
        const { url, requests } = await serve();
        const registry = new Registry({ registryUrl: url, username: 'proxy-user', password: 'proxy-pass' });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].headers['x-openvsx-token']).toBe('the.pat');
        expect(requests[0].headers.authorization).toBe(`Basic ${Buffer.from('proxy-user:proxy-pass').toString('base64')}`);
    });

    it('still sends the token query parameter alongside the header, for a registry that predates header support', async () => {
        const { url, requests } = await serve({ body: { version: '1.2.0' } });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].url.searchParams.get('token')).toBe('the.pat');
    });

    it('drops the token query parameter once the registry is new enough to resolve it from the header', async () => {
        const { url, requests } = await serve({ body: { version: '1.3.0' } });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].url.searchParams.has('token')).toBe(false);
        expect(requests[0].headers.authorization).toBe('Bearer the.pat');
    });

    // A registry whose version can't be placed is treated the same as one confirmed to be too old -
    // it might not recognize the header at all, and dropping the query parameter would then leave a
    // request no such registry can authenticate.
    it('keeps sending the token query parameter when the registry does not expose `/api/version`', async () => {
        const { url, requests } = await serve({ status: 404, body: { error: 'Not Found' } });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].url.searchParams.get('token')).toBe('the.pat');
    });

    it('keeps sending the token query parameter when the reported version is not valid semver', async () => {
        const { url, requests } = await serve({ body: { version: 'not-a-version' } });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');

        expect(requests[0].url.searchParams.get('token')).toBe('the.pat');
    });

    it('checks the registry version only once per Registry instance', async () => {
        let versionRequests = 0;
        const { url } = await serve({ body: { version: '1.3.0' } });
        const server = servers[servers.length - 1];
        server.on('request', req => {
            if (new URL(req.url ?? '/', 'http://127.0.0.1').pathname === '/api/version') {
                versionRequests++;
            }
        });
        const registry = new Registry({ registryUrl: url });

        await registry.verifyPat('foo', 'the.pat');
        await registry.verifyPat('foo', 'the.pat');

        expect(versionRequests).toBe(1);
    });
});
