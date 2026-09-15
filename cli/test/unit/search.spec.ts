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
import { search } from '../../src/search';

interface SearchStub {
    url: string;
    requests: { pathname: string; query: URLSearchParams }[];
    close: () => Promise<void>;
}

const entry = {
    url: 'https://example.test/api/redhat/java',
    files: {},
    namespace: 'redhat',
    name: 'java',
    version: '1.2.0',
    timestamp: '2026-08-01T10:00:00Z',
    downloadCount: 40086502,
    averageRating: 4.75,
    reviewCount: 16,
    displayName: 'Language Support for Java',
    description: 'Java Linting, Intellisense, formatting, refactoring and more',
    deprecated: false
};

async function startSearchStub(status = 200, body?: unknown): Promise<SearchStub> {
    const requests: { pathname: string; query: URLSearchParams }[] = [];
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        requests.push({ pathname: url.pathname, query: url.searchParams });
        res.writeHead(status, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(body ?? { offset: 0, totalSize: 1, extensions: [entry] }));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        requests,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('search', () => {

    const stubs: SearchStub[] = [];
    let log: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        log = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        log.mockRestore();
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
    });

    async function givenRegistry(status?: number, body?: unknown): Promise<SearchStub> {
        const stub = await startSearchStub(status, body);
        stubs.push(stub);
        return stub;
    }

    function output(): string {
        return log.mock.calls.map(call => String(call[0] ?? '')).join('\n');
    }

    it('prints a result row per extension', async () => {
        const registry = await givenRegistry();

        await search({ text: 'java', registryUrl: registry.url });

        expect(registry.requests[0].pathname).toBe('/api/-/search');
        expect(registry.requests[0].query.get('query')).toBe('java');
        const printed = output();
        expect(printed).toContain('redhat.java');
        expect(printed).toContain('1.2.0');
        expect(printed).toContain('40,086,502');
        expect(printed).toContain('4.8');
    });

    it('reports where the page sits in the result set', async () => {
        const registry = await givenRegistry(200, { offset: 20, totalSize: 57, extensions: [entry] });

        await search({ text: 'java', offset: 20, registryUrl: registry.url });

        const printed = output();
        expect(printed).toContain('Showing 21-21 of 57.');
        expect(printed).toContain('Pass --offset 21 for the next page.');
    });

    it('does not offer a next page on the last one', async () => {
        const registry = await givenRegistry(200, { offset: 0, totalSize: 1, extensions: [entry] });

        await search({ text: 'java', registryUrl: registry.url });

        expect(output()).not.toContain('--offset');
    });

    it('says so when nothing matched', async () => {
        const registry = await givenRegistry(200, { offset: 0, totalSize: 0, extensions: [] });

        await search({ text: 'nothing-matches-this', registryUrl: registry.url });

        expect(output()).toContain('No extensions found.');
    });

    it('marks a deprecated result', async () => {
        const registry = await givenRegistry(200, {
            offset: 0,
            totalSize: 1,
            extensions: [{ ...entry, deprecated: true }]
        });

        await search({ text: 'java', registryUrl: registry.url });

        expect(output()).toContain('(deprecated)');
    });

    it('passes the filters and paging through', async () => {
        const registry = await givenRegistry();

        await search({
            text: 'java',
            category: 'Programming Languages',
            target: 'linux-x64',
            sortBy: 'downloadCount',
            sortOrder: 'desc',
            size: 5,
            offset: 10,
            registryUrl: registry.url
        });

        const query = registry.requests[0].query;
        expect(query.get('category')).toBe('Programming Languages');
        expect(query.get('targetPlatform')).toBe('linux-x64');
        expect(query.get('sortBy')).toBe('downloadCount');
        expect(query.get('sortOrder')).toBe('desc');
        expect(query.get('size')).toBe('5');
        expect(query.get('offset')).toBe('10');
    });

    // Browsing by category alone is a legitimate use, so the text is optional.
    it('searches without any text', async () => {
        const registry = await givenRegistry();

        await search({ category: 'Snippets', registryUrl: registry.url });

        expect(registry.requests[0].query.has('query')).toBe(false);
        expect(registry.requests[0].query.get('category')).toBe('Snippets');
    });

    // Caught locally rather than sent on, since the registry answers a bad key with a bare 400.
    it('rejects an unknown sort key before making a request', async () => {
        const registry = await givenRegistry();

        await expect(search({ text: 'java', sortBy: 'downloads', registryUrl: registry.url }))
            .rejects.toThrow('Sort key must be one of relevance, timestamp, rating, downloadCount.');
        expect(registry.requests).toHaveLength(0);
    });

    it('rejects an unknown sort order before making a request', async () => {
        const registry = await givenRegistry();

        await expect(search({ text: 'java', sortOrder: 'sideways', registryUrl: registry.url }))
            .rejects.toThrow('Sort order must be one of asc, desc.');
        expect(registry.requests).toHaveLength(0);
    });

    it('reports the error the registry returns', async () => {
        const registry = await givenRegistry(200, { error: 'Invalid category', offset: 0, totalSize: 0 });

        await expect(search({ text: 'java', registryUrl: registry.url })).rejects.toThrow('Invalid category');
    });

    it('prints raw JSON with --json', async () => {
        const registry = await givenRegistry();

        await search({ text: 'java', json: true, registryUrl: registry.url });

        expect(JSON.parse(output())).toMatchObject({ totalSize: 1 });
    });
});
