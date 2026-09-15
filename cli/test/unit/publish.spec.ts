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
}

interface RegistryStub {
    url: string;
    publishRequests: RecordedRequest[];
    tokenRequests: number;
    close: () => Promise<void>;
}

/**
 * Stands in for the registry's `/api/version` and `/api/-/publish` endpoints.
 */
async function startRegistryStub(
    version: { status?: number; body?: unknown } = {},
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
    const state = { tokenRequests: 0 };
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        req.on('data', () => undefined);
        req.on('end', () => {
            if (url.pathname === '/api/version') {
                res.writeHead(versionStatus, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(versionBody));
            } else if (url.pathname === '/api/-/trusted-publishing/token') {
                state.tokenRequests++;
                res.writeHead(201, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ value: `token-${state.tokenRequests}` }));
            } else {
                const attempt = publishAttempts?.[Math.min(publishRequests.length, publishAttempts.length - 1)];
                publishRequests.push({ pathname: url.pathname, query: url.searchParams });
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
        version?: { status?: number; body?: unknown },
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

    function givenExtensionFile(sizeInBytes: number): string {
        const file = path.join(os.tmpdir(), `ovsx-publish-test-${Math.random().toString(36).slice(2)}.vsix`);
        fs.writeFileSync(file, Buffer.alloc(sizeInBytes));
        tmpFiles.push(file);
        return file;
    }

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

    it('publishes a package that is within the registry size limit', async () => {
        const registry = await givenRegistry({ body: { version: '1.2.0', maxExtensionSize: 1024 } });
        const extensionFile = givenExtensionFile(100);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    it('rejects locally, without uploading, when the package exceeds the registry size limit', async () => {
        const registry = await givenRegistry({ body: { version: '1.2.0', maxExtensionSize: 100 } });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('rejected');
        expect((result as PromiseRejectedResult).reason.message).toBe(
            `The extension package (200 bytes) exceeds the size limit of 100 bytes accepted by the registry at ${registry.url}.`
        );
        expect(registry.publishRequests).toHaveLength(0);
    });

    it('proceeds when the registry does not report a size limit', async () => {
        const registry = await givenRegistry({ body: { version: '1.2.0' } });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    it('proceeds when the registry does not expose `/api/version`', async () => {
        const registry = await givenRegistry({ status: 404, body: {} });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
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
        expect(registry.publishRequests[0].query.get('token')).toBe('token-1');
        expect(registry.publishRequests[1].query.get('token')).toBe('token-2');
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
        expect(registry.publishRequests.map(request => request.query.get('token')))
            .toEqual(['token-1', 'token-1', 'token-1']);
    });
});
