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

import * as http from 'http';
import { AddressInfo } from 'net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { show } from '../../src/show';

interface ShowRequest {
    pathname: string;
    query: URLSearchParams;
}

interface RegistryStub {
    url: string;
    metadataRequests: ShowRequest[];
    versionRequests: ShowRequest[];
    close: () => Promise<void>;
}

const extension = {
    namespace: 'redhat',
    name: 'java',
    version: '1.2.0',
    targetPlatform: 'universal',
    displayName: 'Language Support for Java',
    description: 'Java language support',
    timestamp: '2026-08-01T10:00:00Z',
    versionAlias: ['latest'],
    downloadCount: 1234567,
    averageRating: 4.25,
    reviewCount: 12,
    verified: true,
    publishedBy: { loginName: 'redhat-bot' },
    categories: ['Programming Languages', 'Linters'],
    tags: ['java', '__web_extension'],
    license: 'EPL-2.0',
    repository: 'https://github.com/redhat-developer/vscode-java',
    engines: { vscode: '^1.90.0' },
    extensionKind: ['workspace'],
    publishedWithTrustedPublishing: true,
    preRelease: false
};

/** Stands in for `/api/{namespace}/{extension}` plus the version-reference listing. */
async function startRegistryStub(
    metadata: { status?: number; body?: unknown } = {},
    versions: { status?: number; body?: unknown } = {}
): Promise<RegistryStub> {
    const metadataRequests: ShowRequest[] = [];
    const versionRequests: ShowRequest[] = [];
    const defaultVersions = { offset: 0, totalSize: 1, versions: [{ version: '1.2.0', targetPlatform: 'universal' }] };
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        const request = { pathname: url.pathname, query: url.searchParams };
        const isVersions = url.pathname.endsWith('/version-references');
        (isVersions ? versionRequests : metadataRequests).push(request);
        const stub = isVersions ? versions : metadata;
        res.writeHead(stub.status ?? 200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(stub.body ?? (isVersions ? defaultVersions : extension)));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        metadataRequests,
        versionRequests,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('show', () => {

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
        metadata?: { status?: number; body?: unknown },
        versions?: { status?: number; body?: unknown }
    ): Promise<RegistryStub> {
        const stub = await startRegistryStub(metadata, versions);
        stubs.push(stub);
        return stub;
    }

    /** The printed output as one string, so assertions can be about content rather than layout. */
    function output(): string {
        return log.mock.calls.map(call => String(call[0] ?? '')).join('\n');
    }

    it('rejects a malformed extension identifier', async () => {
        const registry = await givenRegistry();
        await expect(show({ extensionId: 'not-an-id', registryUrl: registry.url }))
            .rejects.toThrow('The extension identifier must have the form `namespace.extension` or `namespace/extension`.');
        expect(registry.metadataRequests).toHaveLength(0);
    });

    it('reports the error the registry returns', async () => {
        const registry = await givenRegistry({ body: { error: 'Extension not found: redhat.java' } });
        await expect(show({ extensionId: 'redhat.java', registryUrl: registry.url }))
            .rejects.toThrow('Extension not found: redhat.java');
    });

    it('prints the identity, publisher and statistics', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        expect(registry.metadataRequests[0].pathname).toBe('/api/redhat/java');
        const printed = output();
        expect(printed).toContain('Language Support for Java');
        expect(printed).toContain('redhat (verified publisher)');
        expect(printed).toContain('1,234,567 downloads');
        expect(printed).toContain('4.3/5 from 12 reviews');
        expect(printed).toContain('redhat.java');
        expect(printed).toContain('EPL-2.0');
    });

    it('hides internal tags', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        expect(printed).toContain('java');
        expect(printed).not.toContain('__web_extension');
    });

    it('reports the registry-specific metadata the Marketplace has no equivalent for', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        expect(printed).toContain('Verified Publisher');
        expect(printed).toContain('Trusted Publishing');
        expect(printed).toContain('Extension Kind');
    });

    it('leads with a deprecation notice and names the replacement', async () => {
        const registry = await givenRegistry({
            body: {
                ...extension,
                deprecated: true,
                replacement: { url: 'https://open-vsx.org/extension/redhat/java-next', displayName: 'Java Next' }
            }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        expect(output()).toContain('Deprecated - superseded by Java Next');
    });

    it('collapses the per-target-platform rows into one row per version', async () => {
        const registry = await givenRegistry({}, {
            body: {
                offset: 0,
                totalSize: 3,
                versions: [
                    { version: '1.2.0', targetPlatform: 'linux-x64' },
                    { version: '1.2.0', targetPlatform: 'win32-x64' },
                    { version: '1.1.0', targetPlatform: 'universal' }
                ]
            }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        expect(registry.versionRequests[0].pathname).toBe('/api/redhat/java/version-references');
        const printed = output();
        expect(printed).toContain('Version History:');
        expect(printed).toContain('linux-x64, win32-x64');
        expect(printed).toContain('1.1.0');
    });

    // The query makes no ordering promise, so the sort has to happen here - and it has to, because
    // the history cap would otherwise drop an arbitrary version rather than the oldest.
    it('orders the version history newest first', async () => {
        const registry = await givenRegistry({}, {
            body: {
                offset: 0,
                totalSize: 3,
                versions: [
                    { version: '1.9.0' },
                    { version: '1.10.0' },
                    { version: '1.2.0' }
                ]
            }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        // 1.10.0 outranks 1.9.0 numerically, which a string sort would get wrong.
        expect(printed.indexOf('1.10.0')).toBeLessThan(printed.indexOf('1.9.0'));
        expect(printed.indexOf('1.9.0')).toBeLessThan(printed.indexOf('1.2.0'));
    });

    // semver.coerce would drop the prerelease identifier, making 1.2.0-alpha.1 compare equal to
    // 1.2.0 and the order unstable. semver.valid keeps it, so the release sorts ahead of its
    // prereleases - and inputs semver rejects still fall through to the string comparison.
    it('orders prereleases below their release', async () => {
        const registry = await givenRegistry({}, {
            body: {
                offset: 0,
                totalSize: 4,
                versions: [
                    { version: '1.2.0-alpha.1' },
                    { version: '1.2.0' },
                    { version: '1.2.0-beta.1' },
                    { version: 'nightly' }
                ]
            }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        expect(printed.indexOf('1.2.0 ')).toBeLessThan(printed.indexOf('1.2.0-beta.1'));
        expect(printed.indexOf('1.2.0-beta.1')).toBeLessThan(printed.indexOf('1.2.0-alpha.1'));
        // Not valid semver, so it sorts after everything that is.
        expect(printed.indexOf('1.2.0-alpha.1')).toBeLessThan(printed.indexOf('nightly'));
    });

    // --target says it scopes the report, so it has to scope the listing too, not just the
    // metadata lookup. The registry serves /api/{ns}/{ext}/{target}/version-references for this.
    it('scopes the version history to --target', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java', target: 'linux-x64', registryUrl: registry.url });

        expect(registry.versionRequests[0].pathname).toBe('/api/redhat/java/linux-x64/version-references');
    });

    // Number(undefined) is NaN, which printed "NaN reviews".
    it('reports a rating with no review count without printing NaN', async () => {
        const registry = await givenRegistry({
            body: { ...extension, averageRating: 4.25, reviewCount: undefined }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        expect(printed).not.toContain('NaN');
        expect(printed).toContain('0 reviews');
    });

    it('caps the version history and says how many were left out', async () => {
        const refs = Array.from({ length: 9 }, (_, i) => ({ version: `1.0.${i}` }));
        const registry = await givenRegistry({}, { body: { offset: 0, totalSize: 9, versions: refs } });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        expect(output()).toContain('... and 3 more');
    });

    it('lists every version with --all-versions', async () => {
        const refs = Array.from({ length: 9 }, (_, i) => ({ version: `1.0.${i}` }));
        const registry = await givenRegistry({}, { body: { offset: 0, totalSize: 9, versions: refs } });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url, allVersions: true });

        const printed = output();
        expect(printed).not.toContain('more (pass --all-versions');
        expect(printed).toContain('1.0.8');
    });

    // totalSize counts version/target-platform pairs rather than versions, so paging has to run off
    // what actually came back. Without this the listing stopped after the first page and quietly
    // under-reported - the reason this doesn't use the query endpoint, which caps at 100 rows.
    it('pages to the end with --all-versions', async () => {
        const firstPage = Array.from({ length: 100 }, (_, i) => ({ version: `1.0.${i}` }));
        const registry = await givenRegistry({}, {
            body: { offset: 0, totalSize: 150, versions: firstPage }
        });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url, allVersions: true });

        // The first page returned 100 of 150, so a second request must follow at the next offset.
        expect(registry.versionRequests).toHaveLength(2);
        expect(registry.versionRequests[0].query.get('offset')).toBe('0');
        expect(registry.versionRequests[1].query.get('offset')).toBe('100');
    });

    it('requests the version named after @ and the given target platform', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java@1.1.0', target: 'linux-x64', registryUrl: registry.url });

        expect(registry.metadataRequests[0].pathname).toBe('/api/redhat/java/linux-x64/1.1.0');
    });

    // A trailing `@` with nothing after it used to be read as "no version", so this silently reported
    // the latest version - the wrong answer for `ovsx show ext@$VERSION` with the variable unset.
    it('rejects an empty version after @', async () => {
        const registry = await givenRegistry();

        await expect(show({ extensionId: 'redhat.java@', registryUrl: registry.url }))
            .rejects.toThrow('A version must follow `@`, as in `namespace.extension@1.2.3`.');
        expect(registry.metadataRequests).toHaveLength(0);
    });

    it('passes a version alias through as given', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java@pre-release', registryUrl: registry.url });

        expect(registry.metadataRequests[0].pathname).toBe('/api/redhat/java/pre-release');
    });

    it('prints raw JSON with --json, without querying the version history', async () => {
        const registry = await givenRegistry();

        await show({ extensionId: 'redhat.java', json: true, registryUrl: registry.url });

        expect(JSON.parse(output())).toMatchObject({ namespace: 'redhat', name: 'java', version: '1.2.0' });
        expect(registry.versionRequests).toHaveLength(0);
    });

    // The listing is only there for the history table, so a registry that doesn't serve it - or one
    // that errors on it - must still produce the rest of the output rather than failing outright.
    it('still prints the summary when the version listing fails', async () => {
        const registry = await givenRegistry({}, { status: 404, body: { error: 'Not found' } });

        await show({ extensionId: 'redhat.java', registryUrl: registry.url });

        const printed = output();
        expect(printed).toContain('Language Support for Java');
        expect(printed).not.toContain('Version History:');
    });
});
