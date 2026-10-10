/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as fs from 'fs';
import * as http from 'http';
import * as os from 'os';
import * as path from 'path';
import { AddressInfo } from 'net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getExtension } from '../../src/get';

const VSIX = 'vsix-bytes';

interface RegistryStub {
    url: string;
    close: () => Promise<void>;
}

/** Serves `acme/tool` at 2.0.0 (latest) and 1.0.0, plus the download for each. */
async function startRegistryStub(overrides: Record<string, unknown> = {}): Promise<RegistryStub> {
    const { downloadName, ...extra } = overrides as { downloadName?: string };
    let base = '';
    const metadata = (version: string) => ({
        namespace: 'acme',
        name: 'tool',
        version,
        targetPlatform: 'universal',
        versionAlias: ['latest'],
        files: { download: `${base}/file/${downloadName ?? `acme.tool-${version}.vsix`}` },
        allVersions: {
            latest: `${base}/api/acme/tool/latest`,
            '2.0.0': `${base}/api/acme/tool/2.0.0`,
            '1.0.0': `${base}/api/acme/tool/1.0.0`
        },
        ...extra
    });
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        if (url.pathname.startsWith('/file/')) {
            res.writeHead(200, { 'Content-Type': 'application/octet-stream' });
            res.end(VSIX);
            return;
        }
        const version = url.pathname === '/api/acme/tool/1.0.0' ? '1.0.0' : '2.0.0';
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(metadata(version)));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    return { url: base, close: () => new Promise<void>(resolve => server.close(() => resolve())) };
}

describe('get', () => {

    const stubs: RegistryStub[] = [];
    let cwd: string;
    let log: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'ovsx-get-'));
        vi.spyOn(process, 'cwd').mockReturnValue(cwd);
        log = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        for (const stub of stubs.splice(0)) {
            await stub.close();
        }
        fs.rmSync(cwd, { recursive: true, force: true });
    });

    async function get(options: { extensionId?: string; version?: string; output?: string; metadata?: boolean },
                       overrides: Record<string, unknown> = {}): Promise<void> {
        const stub = await startRegistryStub(overrides);
        stubs.push(stub);
        // Absolute, because fs.stat resolves against the real cwd, which process.cwd() mocking does not move.
        const output = options.output === undefined ? undefined : path.join(cwd, options.output) + (options.output.endsWith(path.sep) ? path.sep : '');
        await getExtension({ extensionId: 'acme.tool', registryUrl: stub.url, ...options, output });
    }

    function read(...segments: string[]): string {
        return fs.readFileSync(path.join(cwd, ...segments), 'utf-8');
    }

    describe('download', () => {

        it('saves into the current directory under the file name from the download URL', async () => {
            await get({});

            expect(read('acme.tool-2.0.0.vsix')).toBe(VSIX);
        });

        it('decodes percent-encoded characters in the file name', async () => {
            await get({}, { downloadName: 'my%20ext.vsix' });

            expect(read('my ext.vsix')).toBe(VSIX);
        });

        it('saves into an existing directory under the file name from the download URL', async () => {
            fs.mkdirSync(path.join(cwd, 'out'));

            await get({ output: 'out' });

            expect(read('out', 'acme.tool-2.0.0.vsix')).toBe(VSIX);
        });

        it('treats a missing path ending in a separator as a directory and creates it', async () => {
            await get({ output: path.join('new', 'dir') + path.sep });

            expect(read('new', 'dir', 'acme.tool-2.0.0.vsix')).toBe(VSIX);
        });

        it('treats a missing path without a trailing separator as the file name and creates its parents', async () => {
            await get({ output: path.join('new', 'custom.vsix') });

            expect(read('new', 'custom.vsix')).toBe(VSIX);
            expect(fs.readdirSync(path.join(cwd, 'new'))).toEqual(['custom.vsix']);
        });

        it('overwrites an existing file at the output path', async () => {
            fs.writeFileSync(path.join(cwd, 'custom.vsix'), 'old');

            await get({ output: 'custom.vsix' });

            expect(read('custom.vsix')).toBe(VSIX);
        });

        it('rejects when a parent of the output path is a regular file', async () => {
            fs.writeFileSync(path.join(cwd, 'blocker'), 'x');

            await expect(get({ output: path.join('blocker', 'sub', 'a.vsix') })).rejects.toMatchObject({ code: expect.stringMatching(/^(ENOTDIR|EEXIST)$/) });
        });

        it('rejects when the extension has no download URL', async () => {
            await expect(get({}, { files: {} })).rejects.toThrow('does not provide a download URL');
        });
    });

    describe('--metadata', () => {

        it('prints the metadata when no output is given', async () => {
            await get({ metadata: true });

            expect(JSON.parse(log.mock.calls[0][0] as string)).toMatchObject({ namespace: 'acme', name: 'tool', version: '2.0.0' });
            expect(fs.readdirSync(cwd)).toEqual([]);
        });

        it('writes into an existing directory as <namespace>.<name>-<version>.json', async () => {
            fs.mkdirSync(path.join(cwd, 'out'));

            await get({ metadata: true, output: 'out' });

            expect(JSON.parse(read('out', 'acme.tool-2.0.0.json'))).toMatchObject({ version: '2.0.0' });
        });

        it('treats a missing path ending in a separator as a directory and creates it', async () => {
            await get({ metadata: true, output: 'meta' + path.sep });

            expect(JSON.parse(read('meta', 'acme.tool-2.0.0.json'))).toMatchObject({ version: '2.0.0' });
        });

        it('writes to the given file path and creates its parents', async () => {
            await get({ metadata: true, output: path.join('a', 'b', 'tool.json') });

            expect(JSON.parse(read('a', 'b', 'tool.json'))).toMatchObject({ version: '2.0.0' });
        });
    });

    describe('version selection', () => {

        it('downloads the latest version when no constraint is given', async () => {
            await get({});

            expect(fs.existsSync(path.join(cwd, 'acme.tool-2.0.0.vsix'))).toBe(true);
        });

        it('keeps the latest version when it satisfies the constraint', async () => {
            await get({ version: '^2.0.0' });

            expect(fs.existsSync(path.join(cwd, 'acme.tool-2.0.0.vsix'))).toBe(true);
        });

        it('fetches an older version when the latest does not satisfy the constraint', async () => {
            await get({ version: '1.0.0' });

            expect(read('acme.tool-1.0.0.vsix')).toBe(VSIX);
        });

        it('rejects when no published version satisfies the constraint', async () => {
            await expect(get({ version: '^9.0.0' })).rejects.toThrow("has no published version matching '^9.0.0'");
        });
    });

    describe('errors', () => {

        it('rejects a malformed extension identifier', async () => {
            await expect(get({ extensionId: 'not-an-id' })).rejects.toThrow('must have the form');
        });

        it('surfaces an error reported by the registry', async () => {
            await expect(get({}, { error: 'Extension not found: acme.tool' })).rejects.toThrow('Extension not found: acme.tool');
        });
    });
});
