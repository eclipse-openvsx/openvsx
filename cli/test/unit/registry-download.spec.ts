/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, it, expect, afterEach, vi } from 'vitest';
import * as http from 'node:http';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { AddressInfo } from 'node:net';
import { Registry } from '../../src/registry';

// registry.ts imports 'fs', so the mock has to name the same specifier. Everything but
// createWriteStream is the real module; that one is wrapped only to record when the file closes.
const recorder = vi.hoisted(() => ({ events: [] as string[] }));
vi.mock('fs', async importOriginal => {
    const actual = await importOriginal<typeof import('fs')>();
    return {
        ...actual,
        createWriteStream: (...args: Parameters<typeof actual.createWriteStream>) => {
            const stream = actual.createWriteStream(...args);
            stream.on('close', () => recorder.events.push('file closed'));
            return stream;
        }
    };
});

describe('Registry.download', () => {

    const servers: http.Server[] = [];
    const files: string[] = [];

    afterEach(async () => {
        for (const file of files.splice(0)) {
            fs.rmSync(file, { force: true });
        }
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

    function tempPath(suffix: string): string {
        const file = path.join(os.tmpdir(), `ovsx-download-test-${process.pid}-${Math.random().toString(36).slice(2)}${suffix}`);
        files.push(file);
        return file;
    }

    // The promise used to settle when the response ended, which says nothing about the file: a write
    // stream opens and flushes asynchronously, so a caller reading the path straight afterwards could
    // find it empty, or not yet created at all. Small bodies are the worst case, because the response
    // is over before the filesystem has caught up - which is why the tiny public key download was the
    // one that kept failing.
    //
    // Asserted as an ordering rather than by racing it: the file being closed must come first. Timing
    // the race directly makes for a test that only sometimes notices, since it reproduces a few times
    // in a hundred.
    it('resolves only after the file has been closed, not when the response ends', async () => {
        const body = '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA\n-----END PUBLIC KEY-----\n';
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Type': 'text/plain' });
            res.end(body);
        });
        const registry = new Registry({ registryUrl: url });
        const file = tempPath('.pem');

        recorder.events.length = 0;
        await registry.download(file, new URL(`${url}/publickey`));
        recorder.events.push('download resolved');

        expect(recorder.events).toEqual(['file closed', 'download resolved']);
        expect(fs.readFileSync(file, 'utf-8')).toBe(body);
    });

    // An error response used to be piped into the file before the promise rejected, and the write
    // stream was opened before the status was known at all - so a failed download truncated whatever
    // was already at the path. `get` is handed a path the user chose, so that is their file.
    it('leaves an existing file untouched when the download fails', async () => {
        const url = await serve((_, res) => {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end('no such thing');
        });
        const registry = new Registry({ registryUrl: url });
        const file = tempPath('.vsix');
        fs.writeFileSync(file, 'the file the user already had');

        await expect(registry.download(file, new URL(`${url}/missing`))).rejects.toThrow('status 404');

        expect(fs.readFileSync(file, 'utf-8')).toBe('the file the user already had');
    });

    // createNamespace, verifyPat, publish and delete all put the personal access token in a `token`
    // query parameter, and handleError writes an error's message straight to stderr - and so into CI
    // logs. Driven through verifyPat rather than asserted on the helper alone, so the assertion covers
    // the path a real command takes.
    it('does not put the access token in a timeout message', async () => {
        const url = await serve(() => { /* accepts, never responds */ });
        const registry = new Registry({ registryUrl: url, timeout: 200 });
        const pat = 'super-secret-pat-value';

        const message = await Promise.race([
            registry.verifyPat('some-namespace', pat).then(() => 'resolved', (e: Error) => e.message),
            new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
        ]);

        expect(message).toContain('No response from');
        expect(message).not.toContain(pat);
        expect(message).not.toContain('token=');
        expect(message).not.toContain('?');
    });

    // A server that accepts the connection and then says nothing left the command open indefinitely:
    // node's `timeout` option only raises an event, so nothing acted on it.
    it('rejects when the server accepts the connection and never responds', async () => {
        const url = await serve(() => { /* deliberately never responds */ });
        const registry = new Registry({ registryUrl: url, timeout: 200 });
        const file = tempPath('.vsix');

        const outcome = await Promise.race([
            registry.download(file, new URL(`${url}/silent`)).then(() => 'resolved', (e: Error) => e.message),
            new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
        ]);

        expect(outcome).toContain('No response from');
        expect(fs.existsSync(`${file}.part`)).toBe(false);
    });

    // A timeout that fires once the body has started drives both the request's error handler and
    // pipeline's callback, and each has cleanup to do before it can reject. Whichever finished first
    // used to decide what the caller was told, so a third of stalled downloads reported ECONNRESET
    // instead of the timeout. Repeated because a single pass proved nothing while that was true.
    it('reports a download stalled mid-body as a timeout every time', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Length': '1000' });
            res.write(Buffer.alloc(100, 'x'));
            // and then nothing
        });
        const registry = new Registry({ registryUrl: url, timeout: 150 });

        for (let i = 0; i < 8; i++) {
            const file = tempPath('.bin');
            const message = await registry.download(file, new URL(`${url}/stalled`)).then(
                () => 'resolved',
                (err: NodeJS.ErrnoException) => err.message
            );
            expect(message, `iteration ${i}`).toContain('No response from');
            expect(fs.existsSync(`${file}.part`), `iteration ${i}: partial left behind`).toBe(false);
        }
    });

    // Inactivity, not a deadline: a large package arriving slowly must not be cut off, so the timeout
    // has to be reset by every chunk rather than measured from the start of the request.
    it('does not time out a download that is slow but still progressing', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Length': '5' });
            let sent = 0;
            const tick = setInterval(() => {
                res.write('x');
                if (++sent === 5) {
                    clearInterval(tick);
                    res.end();
                }
            }, 120);
        });
        const registry = new Registry({ registryUrl: url, timeout: 300 });
        const file = tempPath('.bin');

        await registry.download(file, new URL(`${url}/slow`));

        // Five chunks 120ms apart is 600ms in total, twice the timeout, but never 300ms without a byte.
        expect(fs.readFileSync(file, 'utf-8')).toBe('xxxxx');
    });

    // A connection dropped mid-body used to leave the promise unsettled forever: pipe installs its
    // own error handler on the response, so the error was swallowed, the write stream never ended and
    // nothing ever resolved or rejected. The partial write also landed on the caller's path.
    it('rejects rather than hanging when the connection drops mid-download', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Length': '1000' });
            res.write(Buffer.alloc(100, 'x'));
            setTimeout(() => res.socket?.destroy(), 30);
        });
        const registry = new Registry({ registryUrl: url });
        const file = tempPath('.vsix');
        fs.writeFileSync(file, 'the file the user already had');

        const outcome = await Promise.race([
            registry.download(file, new URL(`${url}/truncated`)).then(() => 'resolved', () => 'rejected'),
            new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 4000))
        ]);

        expect(outcome).toBe('rejected');
        expect(fs.readFileSync(file, 'utf-8')).toBe('the file the user already had');
        expect(fs.existsSync(`${file}.part`), 'the partial download should be cleaned up').toBe(false);
    });

    it('does not create the file at all when the download fails', async () => {
        const url = await serve((_, res) => {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end('no such thing');
        });
        const registry = new Registry({ registryUrl: url });
        const file = tempPath('.vsix');

        await expect(registry.download(file, new URL(`${url}/missing`))).rejects.toThrow('status 404');

        expect(fs.existsSync(file)).toBe(false);
    });
});
