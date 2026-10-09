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
import { afterEach, describe, expect, it } from 'vitest';
import * as fs from 'node:fs';
import * as http from 'node:http';
import * as net from 'node:net';
import * as os from 'node:os';
import * as path from 'node:path';
import { AddressInfo } from 'node:net';
import { Registry } from '../../src/registry';

interface Received {
    method?: string;
    path: string;
    headers: http.IncomingHttpHeaders;
    body: string;
}

type Handler = (req: Received, res: http.ServerResponse) => void;

describe('Registry redirects', () => {
    const servers: http.Server[] = [];
    const files: string[] = [];

    afterEach(async () => {
        for (const file of files.splice(0)) {
            fs.rmSync(file, { force: true });
        }
        for (const server of servers.splice(0)) {
            server.closeAllConnections();
            await new Promise<void>(resolve => server.close(() => resolve()));
        }
    });

    /** Serves `handler` once each request body has arrived, recording every request. */
    async function serve(handler: Handler): Promise<{ url: string, received: Received[] }> {
        const received: Received[] = [];
        const server = http.createServer((req, res) => {
            let body = '';
            req.setEncoding('utf-8');
            req.on('data', chunk => body += chunk);
            req.on('end', () => {
                const request = { method: req.method, path: new URL(req.url ?? '/', 'http://x').pathname, headers: req.headers, body };
                received.push(request);
                handler(request, res);
            });
        });
        servers.push(server);
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        return { url: `http://127.0.0.1:${(server.address() as AddressInfo).port}`, received };
    }

    function redirect(res: http.ServerResponse, status: number, location: string): void {
        res.writeHead(status, { Location: location });
        res.end();
    }

    function json(res: http.ServerResponse, body: object): void {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(body));
    }

    function tempFile(content: string): string {
        const file = path.join(os.tmpdir(), `ovsx-redirect-test-${process.pid}-${Math.random().toString(36).slice(2)}`);
        files.push(file);
        if (content) {
            fs.writeFileSync(file, content);
        }
        return file;
    }

    // The registry redirects file downloads to its storage, so `get` depends on this.
    it('follows a download redirect to storage on another origin, without the credentials', async () => {
        const storage = await serve((_, res) => {
            res.writeHead(200);
            res.end('vsix bytes');
        });
        const registry = await serve((_, res) => redirect(res, 302, `${storage.url}/file.vsix`));
        const file = tempFile('');
        await new Registry({ registryUrl: registry.url, username: 'user', password: 'secret' })
            .download(file, new URL(`${registry.url}/api/ns/ext/1.0.0/file/file.vsix`));
        expect(fs.readFileSync(file, 'utf-8')).toBe('vsix bytes');
        expect(registry.received[0].headers.authorization).toMatch(/^Basic /);
        expect(storage.received[0].headers.authorization).toBeUndefined();
    });

    it('keeps the credentials on a redirect within the same origin', async () => {
        const server = await serve((req, res) => req.path === '/api/version'
            ? json(res, { version: '1.3.0' })
            : req.path === '/old/verify-pat' ? json(res, { success: 'ok' }) : redirect(res, 301, '/old/verify-pat'));
        await new Registry({ registryUrl: server.url, username: 'user', password: 'secret' }).verifyPat('ns', 'the.pat');
        const final = server.received.find(r => r.path === '/old/verify-pat');
        expect(final?.headers.authorization).toMatch(/^Basic /);
        expect(final?.headers['x-openvsx-token']).toBe('the.pat');
    });

    // With proxy credentials the PAT travels in X-OpenVSX-Token rather than Authorization.
    it('drops X-OpenVSX-Token on a redirect to another origin', async () => {
        const target = await serve((_, res) => json(res, { success: 'ok' }));
        const origin = await serve((req, res) => req.path === '/api/version'
            ? json(res, { version: '1.3.0' })
            : redirect(res, 302, `${target.url}/api/ns/verify-pat`));
        await new Registry({ registryUrl: origin.url, username: 'user', password: 'secret' }).verifyPat('ns', 'the.pat');
        expect(origin.received.find(r => r.path === '/api/ns/verify-pat')?.headers['x-openvsx-token']).toBe('the.pat');
        expect(target.received[0].headers['x-openvsx-token']).toBeUndefined();
        expect(target.received[0].headers.authorization).toBeUndefined();
    });

    it('drops the bearer token on a redirect to another origin', async () => {
        const target = await serve((_, res) => json(res, { success: 'ok' }));
        const origin = await serve((req, res) => req.path === '/api/version'
            ? json(res, { version: '1.3.0' })
            : redirect(res, 302, `${target.url}/api/ns/verify-pat`));
        await new Registry({ registryUrl: origin.url }).verifyPat('ns', 'the.pat');
        expect(target.received[0].headers.authorization).toBeUndefined();
    });

    it('sends the published file again on a 307', async () => {
        const server = await serve((req, res) => {
            if (req.path === '/api/version') {
                json(res, { version: '1.3.0' });
            } else if (req.path === '/api/-/publish') {
                redirect(res, 307, '/v2/publish');
            } else {
                json(res, { name: 'ext' });
            }
        });
        const file = tempFile('package bytes');
        await new Registry({ registryUrl: server.url }).publish(file, 'the.pat');
        const uploads = server.received.filter(r => r.method === 'POST').map(r => [r.path, r.body]);
        expect(uploads).toEqual([['/api/-/publish', 'package bytes'], ['/v2/publish', 'package bytes']]);
    });

    // A proxy can answer before reading the upload and then reset the connection.
    it('follows a 307 sent before the upload is read, then reset', async () => {
        // Answers after the reset, so an error from the abandoned hop would settle first.
        const target = await serve((_, res) => setTimeout(() => json(res, { name: 'ext' }), 300));
        const early = net.createServer(socket => {
            socket.once('data', () => {
                socket.pause();
                socket.write(`HTTP/1.1 307 Temporary Redirect\r\nLocation: ${target.url}/upload\r\nContent-Length: 0\r\n\r\n`);
                setTimeout(() => socket.resetAndDestroy(), 100);
            });
        });
        await new Promise<void>(resolve => early.listen(0, '127.0.0.1', resolve));
        try {
            const file = tempFile('x'.repeat(16 * 1024 * 1024));
            const url = new URL(`http://127.0.0.1:${(early.address() as AddressInfo).port}/upload`);
            await expect(new Registry({ registryUrl: target.url }).postFile(file, url)).resolves.toEqual({ name: 'ext' });
            expect(target.received[0].body.length).toBe(16 * 1024 * 1024);
        } finally {
            await new Promise<void>(resolve => early.close(() => resolve()));
        }
    });

    it('sends a JSON body again on a 308', async () => {
        const server = await serve((req, res) => req.path === '/api/-/trusted-publishing/token'
            ? redirect(res, 308, '/v2/token')
            : json(res, { value: 'token' }));
        await new Registry({ registryUrl: server.url }).requestTrustedPublishingToken('ns', 'ext', 'id.token');
        expect(server.received.map(r => r.body)).toEqual([
            JSON.stringify({ namespace: 'ns', extension: 'ext', token: 'id.token' }),
            JSON.stringify({ namespace: 'ns', extension: 'ext', token: 'id.token' })
        ]);
    });

    it('refuses a 302 for a request with a body instead of resending it as a GET', async () => {
        const server = await serve((req, res) => req.path === '/api/-/trusted-publishing/token'
            ? redirect(res, 302, '/elsewhere')
            : json(res, {}));
        await expect(new Registry({ registryUrl: server.url }).requestTrustedPublishingToken('ns', 'ext', 'id.token'))
            .rejects.toThrow(/redirected to .*\/elsewhere with status 302, which drops the request body/);
        expect(server.received).toHaveLength(1);
    });

    it('gives up after 20 redirects', async () => {
        const server = await serve((_, res) => redirect(res, 302, '/again'));
        await expect(new Registry({ registryUrl: server.url }).getRegistryVersion())
            .rejects.toThrow('was redirected more than 20 times');
        expect(server.received).toHaveLength(21);
    });

    it('refuses a redirect to a protocol other than http(s)', async () => {
        const server = await serve((_, res) => redirect(res, 302, 'ftp://127.0.0.1/file'));
        await expect(new Registry({ registryUrl: server.url }).getRegistryVersion())
            .rejects.toThrow('was redirected to unsupported URL ftp://127.0.0.1/file');
    });

    it('times out a silent server reached through a redirect', async () => {
        const silent = await serve(() => { /* never answers */ });
        const origin = await serve((_, res) => redirect(res, 302, `${silent.url}/api/version`));
        await expect(new Registry({ registryUrl: origin.url, timeout: 100 }).getRegistryVersion())
            .rejects.toThrow('No response from');
    });

    it('refuses a body larger than the limit before sending anything', async () => {
        const server = await serve((_, res) => json(res, {}));
        const file = tempFile('more than four bytes');
        await expect(new Registry({ registryUrl: server.url }).postFile(file, new URL(`${server.url}/upload`), {}, 4))
            .rejects.toThrow('is larger than the limit of 4 bytes');
        await expect(new Registry({ registryUrl: server.url }).post('more than four bytes', new URL(`${server.url}/upload`), {}, 4))
            .rejects.toThrow('is larger than the limit of 4 bytes');
        expect(server.received).toHaveLength(0);
    });
});
