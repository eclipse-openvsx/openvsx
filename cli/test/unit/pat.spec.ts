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
import { createNamespace } from '../../src/create-namespace';
import { unpublish } from '../../src/unpublish';
import { verifyPat } from '../../src/verify-pat';

const prompts = vi.hoisted(() => ({ password: vi.fn(), confirm: vi.fn() }));
vi.mock('@inquirer/prompts', () => prompts);

const store = vi.hoisted(() => ({ get: vi.fn(), add: vi.fn(), delete: vi.fn() }));
vi.mock('../../src/store', () => ({ openDefaultStore: async () => store }));

interface RegistryStub {
    url: string;
    close: () => Promise<void>;
}

/** Answers every request but `/api/version` with the given status and body. */
async function startRegistryStub(status: number, body: unknown): Promise<RegistryStub> {
    const server = http.createServer((req, res) => {
        const versionRequest = req.url?.startsWith('/api/version');
        res.writeHead(versionRequest ? 200 : status, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(versionRequest ? { version: '1.2.0' } : body));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('a prompted personal access token', () => {

    const stubs: RegistryStub[] = [];

    beforeEach(() => {
        vi.spyOn(console, 'log').mockImplementation(() => undefined);
        store.get.mockResolvedValue(undefined);
        prompts.password.mockResolvedValue('typed.pat');
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        prompts.password.mockReset();
        store.get.mockReset();
        store.add.mockReset();
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
    });

    async function givenRegistry(status: number, body: unknown): Promise<string> {
        const stub = await startRegistryStub(status, body);
        stubs.push(stub);
        return stub.url;
    }

    const rejected = { error: 'Invalid access token.' };

    it('is not stored when verify-pat rejects it', async () => {
        const registryUrl = await givenRegistry(200, rejected);
        await expect(verifyPat({ namespace: 'foo', registryUrl })).rejects.toThrow('Invalid access token.');
        expect(store.add).not.toHaveBeenCalled();
    });

    it('is stored once verify-pat accepts it', async () => {
        const registryUrl = await givenRegistry(200, { success: 'Valid token' });
        await verifyPat({ namespace: 'foo', registryUrl });
        expect(store.add).toHaveBeenCalledWith('foo', 'typed.pat');
    });

    it('is not stored when unpublish is rejected', async () => {
        const registryUrl = await givenRegistry(403, { error: 'Forbidden', message: 'Forbidden' });
        await expect(unpublish({ extensionId: 'foo.bar', force: true, registryUrl })).rejects.toThrow('Forbidden');
        expect(store.add).not.toHaveBeenCalled();
    });

    it('is stored once unpublish succeeds', async () => {
        const registryUrl = await givenRegistry(200, { success: 'Deleted 1 version' });
        await unpublish({ extensionId: 'foo.bar', force: true, registryUrl });
        expect(store.add).toHaveBeenCalledWith('foo', 'typed.pat');
    });

    it('is not stored when create-namespace is rejected', async () => {
        const registryUrl = await givenRegistry(200, rejected);
        await expect(createNamespace({ name: 'foo', registryUrl })).rejects.toThrow('Invalid access token.');
        expect(store.add).not.toHaveBeenCalled();
    });

    it('is stored once create-namespace succeeds', async () => {
        const registryUrl = await givenRegistry(201, { success: 'Created namespace foo' });
        await createNamespace({ name: 'foo', registryUrl });
        expect(store.add).toHaveBeenCalledWith('foo', 'typed.pat');
    });

    it('does not fail a successful unpublish when it cannot be stored', async () => {
        store.add.mockRejectedValue(new Error('keychain unavailable'));
        const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        const registryUrl = await givenRegistry(200, { success: 'Deleted 1 version' });
        await unpublish({ extensionId: 'foo.bar', force: true, registryUrl });
        expect(warn).toHaveBeenCalledWith('Could not store the token: keychain unavailable');
    });

    it('is not prompted for when a token is already stored', async () => {
        store.get.mockResolvedValue('stored.pat');
        const registryUrl = await givenRegistry(200, { success: 'Valid token' });
        await verifyPat({ namespace: 'foo', registryUrl });
        expect(prompts.password).not.toHaveBeenCalled();
        expect(store.add).not.toHaveBeenCalled();
    });
});
