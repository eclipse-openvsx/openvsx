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
import { afterEach, describe, expect, it } from 'vitest';
import { Registry } from '../../src/registry';

describe('Registry.getRegistryVersion', () => {
    const servers: http.Server[] = [];

    afterEach(async () => {
        await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve()))));
    });

    /** Answers `/api/version` with the given statuses in turn, the last one repeating. */
    async function startRegistry(...statuses: number[]) {
        const state = { url: '', requests: 0 };
        const server = http.createServer((req, res) => {
            const status = statuses[Math.min(state.requests, statuses.length - 1)];
            state.requests++;
            req.resume();
            res.writeHead(status, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(status === 200 ? { version: '1.7.0' } : { error: 'unavailable' }));
        });
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        state.url = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
        servers.push(server);
        return state;
    }

    it('asks again after a failure instead of remembering it', async () => {
        const registry = await startRegistry(503, 200);
        const client = new Registry({ registryUrl: registry.url });

        await expect(client.getRegistryVersion()).rejects.toThrow();
        await expect(client.getRegistryVersion()).resolves.toMatchObject({ version: '1.7.0' });

        expect(registry.requests).toBe(2);
    });

    it('remembers a success', async () => {
        const registry = await startRegistry(200);
        const client = new Registry({ registryUrl: registry.url });

        await client.getRegistryVersion();
        await client.getRegistryVersion();

        expect(registry.requests).toBe(1);
    });

    it('shares one request between callers that ask at the same time', async () => {
        const registry = await startRegistry(200);
        const client = new Registry({ registryUrl: registry.url });

        await Promise.all([client.getRegistryVersion(), client.getRegistryVersion()]);

        expect(registry.requests).toBe(1);
    });
});
