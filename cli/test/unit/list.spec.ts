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
import { list } from '../../src/list';

interface ListStub {
    url: string;
    requests: string[];
    close: () => Promise<void>;
}

async function startNamespaceStub(body?: unknown): Promise<ListStub> {
    const requests: string[] = [];
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        requests.push(url.pathname);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(body ?? {
            name: 'redhat',
            verified: true,
            extensions: {
                java: 'https://example.test/api/redhat/java',
                ansible: 'https://example.test/api/redhat/ansible'
            }
        }));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        requests,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('list', () => {

    const stubs: ListStub[] = [];
    let log: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        log = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        log.mockRestore();
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
    });

    async function givenRegistry(body?: unknown): Promise<ListStub> {
        const stub = await startNamespaceStub(body);
        stubs.push(stub);
        return stub;
    }

    function lines(): string[] {
        return log.mock.calls.map(call => String(call[0] ?? ''));
    }

    it('lists the namespace and its extensions', async () => {
        const registry = await givenRegistry();

        await list({ namespace: 'redhat', registryUrl: registry.url });

        expect(registry.requests[0]).toBe('/api/redhat');
        const printed = lines().join('\n');
        expect(printed).toContain('redhat (verified) - 2 extensions');
        expect(printed).toContain('redhat.java');
        expect(printed).toContain('redhat.ansible');
    });

    // The response is a JSON object, whose key order is not something to depend on for output that
    // people diff and pipe.
    it('sorts the extensions by name', async () => {
        const registry = await givenRegistry();

        await list({ namespace: 'redhat', registryUrl: registry.url });

        const printed = lines().join('\n');
        expect(printed.indexOf('redhat.ansible')).toBeLessThan(printed.indexOf('redhat.java'));
    });

    it('pluralises a single extension', async () => {
        const registry = await givenRegistry({ name: 'solo', extensions: { only: 'https://example.test' } });

        await list({ namespace: 'solo', registryUrl: registry.url });

        expect(lines()[0]).toBe('solo - 1 extension');
    });

    it('handles an empty namespace', async () => {
        const registry = await givenRegistry({ name: 'empty', extensions: {} });

        await list({ namespace: 'empty', registryUrl: registry.url });

        expect(lines()).toEqual(['empty - 0 extensions']);
    });

    it('omits the verified marker when the namespace is not verified', async () => {
        const registry = await givenRegistry({ name: 'redhat', verified: false, extensions: {} });

        await list({ namespace: 'redhat', registryUrl: registry.url });

        expect(lines()[0]).not.toContain('verified');
    });

    it('reports the error the registry returns', async () => {
        const registry = await givenRegistry({ error: 'Namespace not found: nope' });

        await expect(list({ namespace: 'nope', registryUrl: registry.url }))
            .rejects.toThrow('Namespace not found: nope');
    });

    it('prints raw JSON with --json', async () => {
        const registry = await givenRegistry();

        await list({ namespace: 'redhat', json: true, registryUrl: registry.url });

        expect(JSON.parse(lines().join('\n'))).toMatchObject({ name: 'redhat' });
    });
});
