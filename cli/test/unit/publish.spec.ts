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

import * as crypto from 'crypto';
import * as fs from 'fs';
import * as http from 'http';
import * as os from 'os';
import * as path from 'path';
import { AddressInfo } from 'net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createVSIX } from '@vscode/vsce';
import { publish } from '../../src/publish';
import { buildZip } from './support/zip';

// Only the tests below that publish from a source directory reach this; every other one passes an
// already-packaged file and never packages anything.
vi.mock('@vscode/vsce', () => ({ createVSIX: vi.fn() }));

interface RecordedRequest {
    pathname: string;
    query: URLSearchParams;
    headers: http.IncomingHttpHeaders;
}

interface RegistryStub {
    url: string;
    publishRequests: RecordedRequest[];
    tokenRequests: number;
    versionRequests: number;
    close: () => Promise<void>;
}

/**
 * Stands in for the registry's `/api/version` and `/api/-/publish` endpoints.
 */
async function startRegistryStub(
    version: { status?: number; body?: unknown; sizeLimit?: number } = {},
    publishResponse: {
        status?: number;
        body?: unknown;
        attempts?: Array<{ status: number; body: unknown }>;
    } = {}
): Promise<RegistryStub> {
    const publishRequests: RecordedRequest[] = [];
    const versionStatus = version.status ?? 200;
    const versionBody = version.body ?? { version: '1.2.0' };
    const publishStatus = publishResponse.status ?? 200;
    const publishBody = publishResponse.body ?? {
        namespace: 'foo',
        name: 'bar',
        version: '1.0.0',
        targetPlatform: 'universal'
    };
    // Answers the nth publish with the nth entry, the last one repeating, so a first attempt can be
    // refused and the retry accepted.
    const publishAttempts = publishResponse.attempts;
    const state = { tokenRequests: 0, versionRequests: 0 };
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        req.on('data', () => undefined);
        req.on('end', () => {
            if (url.pathname === '/api/version') {
                state.versionRequests++;
                res.writeHead(versionStatus, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(versionBody));
            } else if (url.pathname === '/api/-/size-limit') {
                // Absent means a registry too old to answer, which is a 404 rather than a limit.
                if (version.sizeLimit === undefined) {
                    res.writeHead(404, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Not found' }));
                } else {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ maxSize: version.sizeLimit }));
                }
            } else if (url.pathname === '/api/-/trusted-publishing/token') {
                state.tokenRequests++;
                res.writeHead(201, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ value: `token-${state.tokenRequests}` }));
            } else {
                const attempt = publishAttempts?.[Math.min(publishRequests.length, publishAttempts.length - 1)];
                publishRequests.push({ pathname: url.pathname, query: url.searchParams, headers: req.headers });
                res.writeHead(attempt?.status ?? publishStatus, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(attempt?.body ?? publishBody));
            }
        });
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        publishRequests,
        get tokenRequests() {
            return state.tokenRequests;
        },
        get versionRequests() {
            return state.versionRequests;
        },
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('publish', () => {

    const stubs: RegistryStub[] = [];
    const tmpFiles: string[] = [];

    beforeEach(() => {
        vi.spyOn(console, 'log').mockImplementation(() => undefined);
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
        tmpFiles.splice(0).forEach(file => fs.rmSync(file, { force: true }));
    });

    async function givenRegistry(
        version?: { status?: number; body?: unknown; sizeLimit?: number },
        publishResponse?: {
            status?: number;
            body?: unknown;
            attempts?: Array<{ status: number; body: unknown }>;
        }
    ): Promise<RegistryStub> {
        const stub = await startRegistryStub(version, publishResponse);
        stubs.push(stub);
        return stub;
    }

    // A real .vsix, because trusted publishing reads the manifest to learn which extension to ask a
    // token for. The publisher/name must differ per test: the token cache is module level and lives
    // for the process.
    async function givenVsixFile(publisher: string, name: string): Promise<string> {
        const zip = await buildZip({
            'extension/package.json': Buffer.from(JSON.stringify({ publisher, name, version: '1.0.0' }))
        });
        const file = path.join(os.tmpdir(), `ovsx-publish-test-${Math.random().toString(36).slice(2)}.vsix`);
        fs.writeFileSync(file, zip);
        tmpFiles.push(file);
        return file;
    }

    /**
     * A real package padded with random bytes to roughly `fillerBytes`. It has to be a real one:
     * publish reads the manifest to learn which namespace to ask for a size limit, so a buffer of
     * zeros is not something that could ever reach a registry. Random, because zeros would compress
     * away and the file would not actually be large.
     */
    async function givenExtensionFile(fillerBytes: number): Promise<string> {
        const zip = await buildZip({
            'extension/package.json': Buffer.from(
                JSON.stringify({ publisher: 'foo', name: 'bar', version: '1.0.0' })
            ),
            'extension/filler.bin': crypto.randomBytes(fillerBytes)
        });
        const file = path.join(os.tmpdir(), `ovsx-publish-test-${Math.random().toString(36).slice(2)}.vsix`);
        fs.writeFileSync(file, zip);
        tmpFiles.push(file);
        return file;
    }

    const sizeOf = (file: string): number => fs.statSync(file).size;

    // The packaging options ovsx hands to vsce are the whole of its packaging behaviour, so the ones a
    // caller sets have to arrive there intact - `--follow-symlinks` above all, which is what makes the
    // file walk work for a symlinked node_modules such as pnpm's.
    it('forwards the packaging options to vsce', async () => {
        const registry = await givenRegistry({ body: { version: '1.2.0' } });
        vi.mocked(createVSIX).mockImplementation(async (options) => {
            fs.writeFileSync(options!.packagePath!, await buildZip({
                'extension/package.json': Buffer.from(JSON.stringify({ publisher: 'foo', name: 'bar', version: '1.0.0' }))
            }));
        });

        const [result] = await publish({
            packagePath: ['.'],
            pat: 'the.pat',
            registryUrl: registry.url,
            yarn: true,
            followSymlinks: true,
            dependencies: false
        });

        expect(result.status).toBe('fulfilled');
        expect(vi.mocked(createVSIX)).toHaveBeenCalledTimes(1);
        expect(vi.mocked(createVSIX).mock.calls[0][0]).toMatchObject({
            cwd: '.',
            useYarn: true,
            followSymlinks: true,
            dependencies: false
        });
    });

    describe('removes the package it created', () => {

        // Records where vsce was asked to write, so the test can check that file is gone afterwards.
        function givenPackaging(): string[] {
            const written: string[] = [];
            vi.mocked(createVSIX).mockImplementation(async (options) => {
                written.push(options!.packagePath!);
                tmpFiles.push(options!.packagePath!);
                fs.writeFileSync(options!.packagePath!, await buildZip({
                    'extension/package.json': Buffer.from(JSON.stringify({ publisher: 'foo', name: 'bar', version: '1.0.0' }))
                }));
            });
            return written;
        }

        it('after publishing it', async () => {
            const registry = await givenRegistry();
            const written = givenPackaging();

            const [result] = await publish({ packagePath: ['.'], pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('fulfilled');
            expect(written).toHaveLength(1);
            expect(fs.existsSync(written[0])).toBe(false);
        });

        it('when the registry rejects it', async () => {
            const registry = await givenRegistry({}, { status: 400, body: { error: 'Something went wrong.' } });
            const written = givenPackaging();

            const [result] = await publish({ packagePath: ['.'], pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('rejected');
            expect(written).toHaveLength(1);
            expect(fs.existsSync(written[0])).toBe(false);
        });

        it('when it is skipped as a duplicate', async () => {
            const registry = await givenRegistry({}, { status: 400, body: { error: 'foo.bar 1.0.0 is already published.' } });
            const written = givenPackaging();

            const [result] = await publish({ packagePath: ['.'], pat: 'the.pat', registryUrl: registry.url, skipDuplicate: true });

            expect(result.status).toBe('fulfilled');
            expect(written).toHaveLength(1);
            expect(fs.existsSync(written[0])).toBe(false);
        });

        it('when it exceeds the registry size limit', async () => {
            const registry = await givenRegistry({ body: { version: '1.2.0', maxExtensionSize: 1 } });
            const written = givenPackaging();

            const [result] = await publish({ packagePath: ['.'], pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('rejected');
            expect(registry.publishRequests).toHaveLength(0);
            expect(fs.existsSync(written[0])).toBe(false);
        });

        it('when packaging fails after writing part of it', async () => {
            const registry = await givenRegistry();
            const written: string[] = [];
            vi.mocked(createVSIX).mockImplementation(async (options) => {
                written.push(options!.packagePath!);
                tmpFiles.push(options!.packagePath!);
                fs.writeFileSync(options!.packagePath!, 'partial');
                throw new Error('Packaging failed.');
            });

            const [result] = await publish({ packagePath: ['.'], pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('rejected');
            expect(registry.publishRequests).toHaveLength(0);
            expect(fs.existsSync(written[0])).toBe(false);
        });

        it('for every target of a fan-out', async () => {
            const registry = await givenRegistry();
            const written = givenPackaging();

            const results = await publish({
                packagePath: ['.'],
                pat: 'the.pat',
                registryUrl: registry.url,
                targets: ['linux-x64', 'darwin-arm64']
            });

            expect(results.map(result => result.status)).toEqual(['fulfilled', 'fulfilled']);
            expect(written).toHaveLength(2);
            expect(written.filter(file => fs.existsSync(file))).toEqual([]);
        });
    });

    describe('keeps a package the user supplied', () => {

        it('given as --packagePath', async () => {
            const registry = await givenRegistry();
            const packagePath = givenExtensionFile(100);

            const [result] = await publish({ packagePath: [packagePath], pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('fulfilled');
            expect(fs.existsSync(packagePath)).toBe(true);
        });

        it('given as --extensionFile, even when the registry rejects it', async () => {
            const registry = await givenRegistry({}, { status: 400, body: { error: 'Something went wrong.' } });
            const extensionFile = givenExtensionFile(100);

            const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

            expect(result.status).toBe('rejected');
            expect(fs.existsSync(extensionFile)).toBe(true);
        });
    });

    it('publishes a package the namespace limit allows', async () => {
        const extensionFile = await givenExtensionFile(2000);
        const registry = await givenRegistry({
            body: { version: '1.3.0', maxExtensionSize: 100 },
            sizeLimit: sizeOf(extensionFile) + 1
        });

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    // The namespace limit is authoritative, so there is no false negative left to worry about and
    // the upload can be refused before it is sent.
    it('refuses a package above the limit the registry reports for its namespace', async () => {
        const extensionFile = await givenExtensionFile(2000);
        const registry = await givenRegistry({
            body: { version: '1.3.0', maxExtensionSize: 10_000_000 },
            sizeLimit: sizeOf(extensionFile) - 1
        });

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('rejected');
        expect((result as PromiseRejectedResult).reason.message).toContain('exceeds the size limit');
        expect((result as PromiseRejectedResult).reason.message).toContain('foo.bar');
        expect(registry.publishRequests).toHaveLength(0);
    });

    // ovsx publishes to registries of every age. A release that refused to publish anywhere without
    // the new endpoint would be worse than the false negative it is fixing.
    it('falls back to warning when the registry predates the size-limit endpoint', async () => {
        const extensionFile = await givenExtensionFile(2000);
        const registry = await givenRegistry({ body: { version: '1.2.0', maxExtensionSize: 100 } });

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
        expect(console.warn).toHaveBeenCalledWith(
            expect.stringContaining('exceeds the default size limit of 100 bytes')
        );
    });

    /**
     * A registry new enough to be asked, whose answer never arrives - a misrouted /api/-/*, a WAF, a
     * 502. Staying quiet made that indistinguishable from a working preflight, and since an override
     * can lower a limit as well as raise it, no package is small enough for silence to be safe.
     */
    it('warns when a registry new enough to answer the size-limit lookup does not', async () => {
        // comfortably under the default, so nothing else would have had cause to say anything
        const extensionFile = await givenExtensionFile(200);
        const registry = await givenRegistry({ body: { version: '1.3.0', maxExtensionSize: 10_000_000 } });

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
        expect(console.warn).toHaveBeenCalledWith(
            expect.stringContaining('Could not check the size limit for foo.bar')
        );
    });

    it('proceeds when the registry does not report a size limit', async () => {
        const extensionFile = await givenExtensionFile(200);
        const registry = await givenRegistry({ body: { version: '1.2.0' } });

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    it('proceeds when the registry does not expose `/api/version`', async () => {
        const registry = await givenRegistry({ status: 404, body: {} });
        const extensionFile = await givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    // The size-limit lookup and the token-header version check (Registry.tokenQuery) both fetch
    // /api/version, and every target used to get its own Registry instance - looking that up once for
    // the size limit and once per target added up fast for a wide fan-out. All targets now share the
    // one Registry created up front, so this is a single request regardless of fan-out width.
    it('looks up the registry version only once across a fan-out of targets', async () => {
        const registry = await givenRegistry({ body: { version: '1.3.0' } });
        const extensionFile = await givenExtensionFile(200);

        const results = await publish({
            extensionFile,
            pat: 'the.pat',
            registryUrl: registry.url,
            targets: ['linux-x64', 'darwin-arm64', 'win32-x64']
        });

        expect(results.every(result => result.status === 'fulfilled')).toBe(true);
        expect(registry.publishRequests).toHaveLength(3);
        expect(registry.versionRequests).toBe(1);
    });

    // The trusted publishing token is short-lived and shared by every target platform of a release, so
    // a slow fan-out can outlive it and be refused partway through. Failing a release that was
    // authorised, over an expiry, would be the wrong call.
    it('asks for a new token and retries when the registry refuses the one it was publishing with', async () => {
        const registry = await givenRegistry(
            {},
            {
                attempts: [
                    { status: 401, body: { error: 'Invalid access token.' } },
                    { status: 200, body: { namespace: 'foo', name: 'retried', version: '1.0.0', targetPlatform: 'universal' } }
                ]
            }
        );
        const extensionFile = await givenVsixFile('foo', 'retried');

        const [result] = await publish({
            extensionFile,
            registryUrl: registry.url,
            trustedPublishing: true,
            idToken: 'an-id-token'
        });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(2);
        // the retry carries the replacement, not the token that was just refused
        expect(registry.publishRequests[0].headers.authorization).toBe('Bearer token-1');
        expect(registry.publishRequests[1].headers.authorization).toBe('Bearer token-2');
        expect(registry.tokenRequests).toBe(2);
    });

    // Nothing the CLI can do makes a token the user supplied valid. An ID token is offered here as
    // well, so that only the token's origin - not the absence of a CI identity to exchange - can be
    // what stops the retry.
    it('does not retry a refusal when the token came from the user', async () => {
        const registry = await givenRegistry({}, { status: 401, body: { error: 'Invalid access token.' } });
        const extensionFile = await givenVsixFile('foo', 'user-pat');

        const [result] = await publish({
            extensionFile,
            pat: 'the.pat',
            registryUrl: registry.url,
            idToken: 'an-id-token'
        });

        expect(result.status).toBe('rejected');
        expect(registry.publishRequests).toHaveLength(1);
        expect(registry.tokenRequests).toBe(0);
    });

    // A second refusal is not an expiry: the token is not the problem, and retrying forever would hide
    // whatever is.
    it('gives up when the replacement token is refused too', async () => {
        const registry = await givenRegistry({}, { status: 401, body: { error: 'Invalid access token.' } });
        const extensionFile = await givenVsixFile('foo', 'refused-twice');

        const [result] = await publish({
            extensionFile,
            registryUrl: registry.url,
            trustedPublishing: true,
            idToken: 'an-id-token'
        });

        expect(result.status).toBe('rejected');
        expect(registry.publishRequests).toHaveLength(2);
        expect(registry.tokenRequests).toBe(2);
    });

    // What a multi target release looks like: one package per target platform, published in a single
    // invocation. The point is the token - the identity is exchanged once and every package publishes
    // with it, which is what the registry has to accept.
    it('publishes every package of a release with one exchanged token', async () => {
        const registry = await givenRegistry();
        const packagePath = await Promise.all([
            givenVsixFile('foo', 'fanned-out'),
            givenVsixFile('foo', 'fanned-out'),
            givenVsixFile('foo', 'fanned-out')
        ]);

        const results = await publish({
            packagePath,
            registryUrl: registry.url,
            trustedPublishing: true,
            idToken: 'an-id-token'
        });

        expect(results.map(result => result.status)).toEqual(['fulfilled', 'fulfilled', 'fulfilled']);
        expect(registry.publishRequests).toHaveLength(3);
        expect(registry.tokenRequests).toBe(1);
        expect(registry.publishRequests.map(request => request.headers.authorization))
            .toEqual(['Bearer token-1', 'Bearer token-1', 'Bearer token-1']);
    });
});
