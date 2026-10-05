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
import * as fs from 'node:fs';
import * as http from 'node:http';
import * as os from 'node:os';
import * as path from 'node:path';
import { AddressInfo } from 'node:net';
import { Registry } from '../../src/registry';

describe('Registry JSON requests', () => {

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

    // A connection lost after the headers have arrived never fires 'end' on the response, and the
    // response's own error had no listener - so the promise was never settled and the command waited
    // on a body that was not coming. Asserted with the timeout switched off, since a timeout would
    // otherwise mask the hang by eventually rejecting for the wrong reason.
    it('rejects rather than hanging when the connection drops mid-response', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.write('{"namespace":"foo"');
            setTimeout(() => res.socket?.destroy(), 30);
        });
        const registry = new Registry({ registryUrl: url, timeout: 0 });

        const outcome = await Promise.race([
            registry.getJson(new URL(`${url}/api/foo`)).then(
                () => 'resolved',
                (err: NodeJS.ErrnoException) => `rejected: ${err.code ?? err.message}`
            ),
            new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
        ]);

        // fetch's own socket error, not the bare 'terminated' TypeError it is wrapped in
        expect(outcome).toBe('rejected: UND_ERR_SOCKET');
    });

    // fetch reports every network failure as 'fetch failed', which tells a user nothing.
    it('reports why a connection failed rather than a generic fetch error', async () => {
        const url = await serve(() => undefined);
        const server = servers.pop()!;
        await new Promise<void>(resolve => server.close(() => resolve()));
        const registry = new Registry({ registryUrl: url });

        const err: NodeJS.ErrnoException = await registry.getJson(new URL(`${url}/api/foo`)).catch(e => e);

        expect(err.code).toBe('ECONNREFUSED');
    });

    it('follows a redirect for a JSON request', async () => {
        const url = await serve((req, res) => {
            if (req.url === '/api/old') {
                res.writeHead(301, { Location: '/api/new' });
                res.end();
            } else {
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: req.url }));
            }
        });
        const registry = new Registry({ registryUrl: url });

        await expect(registry.getJson(new URL(`${url}/api/old`))).resolves.toEqual({ success: '/api/new' });
    });

    describe('request body limits', () => {
        async function serveRecording(): Promise<{ url: string; paths: string[] }> {
            const paths: string[] = [];
            const url = await serve((req, res) => {
                const path = new URL(req.url ?? '/', 'http://127.0.0.1').pathname;
                res.writeHead(200, { 'Content-Type': 'application/json' });
                if (path === '/api/version') {
                    res.end(JSON.stringify({ version: '1.3.0' }));
                } else {
                    paths.push(path);
                    req.resume();
                    req.on('end', () => res.end(JSON.stringify({ success: 'ok' })));
                }
            });
            return { url, paths };
        }

        it('refuses a namespace request larger than maxNamespaceSize without sending it', async () => {
            const { url, paths } = await serveRecording();
            const registry = new Registry({ registryUrl: url, maxNamespaceSize: 10 });

            await expect(registry.createNamespace('a-rather-long-namespace', 'the.pat')).rejects.toThrow('exceeds the limit of 10 bytes');
            expect(paths).toEqual([]);
        });

        it('refuses a package larger than maxPublishSize without sending it', async () => {
            const { url, paths } = await serveRecording();
            const registry = new Registry({ registryUrl: url, maxPublishSize: 10 });
            const file = path.join(os.tmpdir(), `ovsx-registry-test-${process.pid}-${Math.random().toString(36).slice(2)}.vsix`);
            fs.writeFileSync(file, Buffer.alloc(11));
            try {
                await expect(registry.publish(file, 'the.pat')).rejects.toThrow('exceeds the limit of 10 bytes');
                expect(paths).toEqual([]);
            } finally {
                fs.rmSync(file, { force: true });
            }
        });

        it('streams a package within maxPublishSize', async () => {
            const { url, paths } = await serveRecording();
            const registry = new Registry({ registryUrl: url, maxPublishSize: 10 });
            const file = path.join(os.tmpdir(), `ovsx-registry-test-${process.pid}-${Math.random().toString(36).slice(2)}.vsix`);
            fs.writeFileSync(file, Buffer.alloc(10));
            try {
                await expect(registry.publish(file, 'the.pat')).resolves.toEqual({ success: 'ok' });
                expect(paths).toEqual(['/api/-/publish']);
            } finally {
                fs.rmSync(file, { force: true });
            }
        });
    });

    // The timeout can also fire once part of the body has arrived. The request's error has to be what
    // settles the promise there, so the message says what happened rather than reporting a reset.
    it('reports a stalled response as a timeout, not as a reset', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.write('{"namespace":"foo"');
        });
        const registry = new Registry({ registryUrl: url, timeout: 200 });

        await expect(registry.getJson(new URL(`${url}/api/foo`))).rejects.toThrow('No response from');
    });
});
