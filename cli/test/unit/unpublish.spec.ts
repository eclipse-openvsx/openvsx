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
import { unpublish } from '../../src/unpublish';

interface DeleteRequest {
    method?: string;
    pathname: string;
    query: URLSearchParams;
    body: unknown;
}

interface RegistryStub {
    url: string;
    requests: DeleteRequest[];
    versionRequests: DeleteRequest[];
    close: () => Promise<void>;
}

/**
 * Stands in for the registry's `/api/{namespace}/{extension}/delete` endpoint, plus
 * `/api/version` (reported as supporting `unpublish` unless overridden).
 */
async function startRegistryStub(
    status: number = 200,
    body: unknown = { success: 'Deleted 1 version' },
    version: { status?: number; body?: unknown } = {}
): Promise<RegistryStub> {
    const requests: DeleteRequest[] = [];
    const versionRequests: DeleteRequest[] = [];
    const versionStatus = version.status ?? 200;
    const versionBody = version.body ?? { version: '1.2.0' };
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        let raw = '';
        req.on('data', chunk => raw += chunk);
        req.on('end', () => {
            const request = {
                method: req.method,
                pathname: url.pathname,
                query: url.searchParams,
                body: raw.length > 0 ? JSON.parse(raw) : undefined
            };
            if (url.pathname === '/api/version') {
                versionRequests.push(request);
                res.writeHead(versionStatus, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(versionBody));
            } else {
                requests.push(request);
                res.writeHead(status, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(body));
            }
        });
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        requests,
        versionRequests,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('unpublish', () => {

    const stubs: RegistryStub[] = [];
    let log: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        log = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        log.mockRestore();
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
    });

    async function givenRegistry(
        status?: number,
        body?: unknown,
        version?: { status?: number; body?: unknown }
    ): Promise<RegistryStub> {
        const stub = await startRegistryStub(status, body, version);
        stubs.push(stub);
        return stub;
    }

    it('rejects a malformed extension identifier', async () => {
        const registry = await givenRegistry();
        await expect(unpublish({ extensionId: 'not-an-id', pat: 'the.pat', force: true, registryUrl: registry.url }))
            .rejects.toThrow('The extension identifier must have the form `namespace.extension` or `namespace/extension`.');
        expect(registry.requests).toHaveLength(0);
    });

    it("requires '--versions' when '--target' is given", async () => {
        const registry = await givenRegistry();
        await expect(
            unpublish({
                extensionId: 'foo.bar',
                pat: 'the.pat',
                force: true,
                targets: ['linux-x64'],
                registryUrl: registry.url
            })
        ).rejects.toThrow("Please specify the versions to delete with '--versions' when using '--target'.");
        expect(registry.requests).toHaveLength(0);
    });

    it("aborts without confirmation unless '--force' is given", async () => {
        // stdin isn't a TTY under the test runner, which takes the same guarded path as a non-interactive
        // CI shell - exactly the case this guard exists for.
        const registry = await givenRegistry();
        await expect(unpublish({ extensionId: 'foo.bar', pat: 'the.pat', registryUrl: registry.url }))
            .rejects.toThrow("Aborted. Use '--force' to delete without confirmation.");
        expect(registry.requests).toHaveLength(0);
    });

    it('deletes the extension as a whole when no versions are given', async () => {
        const registry = await givenRegistry(200, { success: 'Deleted namespace.foo, extension bar entirely' });

        await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

        expect(registry.requests).toHaveLength(1);
        const [request] = registry.requests;
        expect(request.method).toBe('POST');
        expect(request.pathname).toBe('/api/foo/bar/delete');
        expect(request.query.get('token')).toBe('the.pat');
        expect(request.query.get('allVersions')).toBe('true');
        expect(request.body).toBeUndefined();
        expect(log).toHaveBeenCalledWith(expect.stringContaining('Deleted namespace.foo, extension bar entirely'));
    });

    it('deletes only the given versions, for every target platform', async () => {
        const registry = await givenRegistry();

        await unpublish({
            extensionId: 'foo.bar',
            pat: 'the.pat',
            force: true,
            versions: ['1.0.0', '1.0.1'],
            registryUrl: registry.url
        });

        expect(registry.requests).toHaveLength(1);
        const [request] = registry.requests;
        expect(request.query.get('token')).toBe('the.pat');
        expect(request.query.has('allVersions')).toBe(false);
        expect(request.body).toEqual([{ version: '1.0.0' }, { version: '1.0.1' }]);
    });

    it('deletes the cross product of the given versions and target platforms', async () => {
        const registry = await givenRegistry();

        await unpublish({
            extensionId: 'foo.bar',
            pat: 'the.pat',
            force: true,
            versions: ['1.0.0', '1.0.1'],
            targets: ['linux-x64', 'darwin-arm64'],
            registryUrl: registry.url
        });

        expect(registry.requests).toHaveLength(1);
        expect(registry.requests[0].body).toEqual([
            { version: '1.0.0', targetPlatform: 'linux-x64' },
            { version: '1.0.0', targetPlatform: 'darwin-arm64' },
            { version: '1.0.1', targetPlatform: 'linux-x64' },
            { version: '1.0.1', targetPlatform: 'darwin-arm64' }
        ]);
    });

    it('reports when the registry had nothing to delete', async () => {
        const registry = await givenRegistry(200, {});

        await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

        expect(log).toHaveBeenCalledWith(expect.stringContaining('Nothing to delete for foo.bar'));
    });

    it('fails with the error reported by the registry', async () => {
        const registry = await givenRegistry(200, { error: 'Insufficient access rights for namespace: foo' });

        await expect(unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url }))
            .rejects.toThrow('Insufficient access rights for namespace: foo');
    });

    it('fails when the registry responds with an error status', async () => {
        const registry = await givenRegistry(403, { error: 'Forbidden', message: 'Forbidden' });

        await expect(unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url }))
            .rejects.toThrow('Forbidden');
    });

    describe('registry version check', () => {
        it('refuses to delete on a registry older than 1.2.0', async () => {
            const registry = await givenRegistry(200, undefined, { body: { version: '1.1.1' } });

            await expect(unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url }))
                .rejects.toThrow(
                    `The registry at ${registry.url} runs version 1.1.1, but deleting extensions requires version 1.2.0 or later.`
                );
            expect(registry.versionRequests).toHaveLength(1);
            expect(registry.requests).toHaveLength(0);
        });

        it('proceeds when the registry is on 1.2.0 or later', async () => {
            const registry = await givenRegistry(200, undefined, { body: { version: '1.2.0' } });

            await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

            expect(registry.requests).toHaveLength(1);
        });

        it('proceeds when the registry does not expose `/api/version`', async () => {
            const registry = await givenRegistry(200, undefined, { status: 404, body: {} });

            await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

            expect(registry.requests).toHaveLength(1);
        });

        it('proceeds when the reported version is not valid semver', async () => {
            const registry = await givenRegistry(200, undefined, { body: { version: 'unknown' } });

            await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

            expect(registry.requests).toHaveLength(1);
        });

        it('proceeds on a development build of a supported release, e.g. `1.2.0-dev.0`', async () => {
            const registry = await givenRegistry(200, undefined, { body: { version: '1.2.0-dev.0' } });

            await unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url });

            expect(registry.requests).toHaveLength(1);
        });

        it('refuses a development build of an unsupported release, e.g. `1.1.9-dev.0`', async () => {
            const registry = await givenRegistry(200, undefined, { body: { version: '1.1.9-dev.0' } });

            await expect(unpublish({ extensionId: 'foo.bar', pat: 'the.pat', force: true, registryUrl: registry.url }))
                .rejects.toThrow(
                    `The registry at ${registry.url} runs version 1.1.9-dev.0, but deleting extensions requires version 1.2.0 or later.`
                );
            expect(registry.requests).toHaveLength(0);
        });
    });

    describe('extension identifier from package.json', () => {
        let cwd: string;
        let tmpDir: string;

        beforeEach(() => {
            cwd = process.cwd();
            tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ovsx-unpublish-'));
            process.chdir(tmpDir);
        });

        afterEach(() => {
            process.chdir(cwd);
            fs.rmSync(tmpDir, { recursive: true, force: true });
        });

        it('is read from the current directory when omitted', async () => {
            fs.writeFileSync(path.join(tmpDir, 'package.json'), JSON.stringify({ publisher: 'foo', name: 'bar' }));
            const registry = await givenRegistry();

            await unpublish({ pat: 'the.pat', force: true, registryUrl: registry.url });

            expect(registry.requests).toHaveLength(1);
            expect(registry.requests[0].pathname).toBe('/api/foo/bar/delete');
        });

        it('fails with an actionable message when no package.json is present', async () => {
            const registry = await givenRegistry();

            await expect(unpublish({ pat: 'the.pat', force: true, registryUrl: registry.url }))
                .rejects.toThrow('Unable to read the extension identifier.');
            expect(registry.requests).toHaveLength(0);
        });
    });
});
