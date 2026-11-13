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
import login from '../../src/login';
import logout from '../../src/logout';
import { getStoredPAT, patKey } from '../../src/pat';
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
        expect(store.add).toHaveBeenCalledWith(patKey('foo', { registryUrl }), 'typed.pat');
    });

    it('is not stored when unpublish is rejected', async () => {
        const registryUrl = await givenRegistry(403, { error: 'Forbidden', message: 'Forbidden' });
        await expect(unpublish({ extensionId: 'foo.bar', force: true, registryUrl })).rejects.toThrow('Forbidden');
        expect(store.add).not.toHaveBeenCalled();
    });

    it('is stored once unpublish succeeds', async () => {
        const registryUrl = await givenRegistry(200, { success: 'Deleted 1 version' });
        await unpublish({ extensionId: 'foo.bar', force: true, registryUrl });
        expect(store.add).toHaveBeenCalledWith(patKey('foo', { registryUrl }), 'typed.pat');
    });

    it('is not stored when create-namespace is rejected', async () => {
        const registryUrl = await givenRegistry(200, rejected);
        await expect(createNamespace({ name: 'foo', registryUrl })).rejects.toThrow('Invalid access token.');
        expect(store.add).not.toHaveBeenCalled();
    });

    it('is stored once create-namespace succeeds', async () => {
        const registryUrl = await givenRegistry(201, { success: 'Created namespace foo' });
        await createNamespace({ name: 'foo', registryUrl });
        expect(store.add).toHaveBeenCalledWith(patKey('foo', { registryUrl }), 'typed.pat');
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

describe('a stored personal access token', () => {
    const stubs: RegistryStub[] = [];
    let entries: Map<string, string>;

    beforeEach(() => {
        vi.spyOn(console, 'log').mockImplementation(() => undefined);
        vi.stubEnv('OVSX_REGISTRY_URL', undefined);
        entries = new Map();
        store.get.mockImplementation(async (key: string) => entries.get(key));
        store.add.mockImplementation(async (key: string, value: string) => void entries.set(key, value));
        store.delete.mockImplementation(async (key: string) => {
            if (!entries.delete(key)) {
                throw new Error('Password not found');
            }
        });
        prompts.password.mockResolvedValue('typed.pat');
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        prompts.password.mockReset();
        vi.unstubAllEnvs();
        Object.values(store).forEach(method => method.mockReset());
        await Promise.all(stubs.splice(0).map(stub => stub.close()));
    });

    async function givenRegistry(): Promise<string> {
        const stub = await startRegistryStub(200, { success: 'Valid token' });
        stubs.push(stub);
        return stub.url;
    }

    it('is keyed by registry host and namespace', () => {
        expect(patKey('foo', {})).toBe('foo@open-vsx.org');
        expect(patKey('foo', { registryUrl: 'https://open-vsx.org/' })).toBe('foo@open-vsx.org');
        expect(patKey('foo', { registryUrl: 'http://127.0.0.1:3000' })).toBe('foo@127.0.0.1_3000');
    });

    it('is not sent to another registry with the same namespace', async () => {
        entries.set(patKey('foo', {}), 'open-vsx.pat');
        const registryUrl = await givenRegistry();
        await verifyPat({ namespace: 'foo', registryUrl });
        expect(prompts.password).toHaveBeenCalledOnce();
        expect(entries.get(patKey('foo', {}))).toBe('open-vsx.pat');
        expect(entries.get(patKey('foo', { registryUrl }))).toBe('typed.pat');
    });

    it('is read from the namespace-only key for the default registry', async () => {
        entries.set('foo', 'legacy.pat');
        expect(await getStoredPAT(store, 'foo', {})).toBe('legacy.pat');
        expect(await getStoredPAT(store, 'foo', { registryUrl: 'https://open-vsx.org/' })).toBe('legacy.pat');
    });

    it('is not read from the namespace-only key for another registry', async () => {
        entries.set('foo', 'legacy.pat');
        const registryUrl = await givenRegistry();
        await verifyPat({ namespace: 'foo', registryUrl });
        expect(prompts.password).toHaveBeenCalledOnce();
    });

    it('is stored by login under the registry it was verified against', async () => {
        const registryUrl = await givenRegistry();
        await login({ namespace: 'foo', registryUrl });
        expect([...entries]).toEqual([[patKey('foo', { registryUrl }), 'typed.pat']]);
    });

    it('is removed by logout only for the given registry', async () => {
        const registryUrl = 'http://127.0.0.1:3000';
        entries.set(patKey('foo', {}), 'open-vsx.pat');
        entries.set(patKey('foo', { registryUrl }), 'self-hosted.pat');
        await logout({ namespace: 'foo', registryUrl });
        expect([...entries]).toEqual([[patKey('foo', {}), 'open-vsx.pat']]);
    });

    it('is removed by logout from both keys of the default registry', async () => {
        entries.set('foo', 'legacy.pat');
        entries.set(patKey('foo', {}), 'open-vsx.pat');
        await logout({ namespace: 'foo' });
        expect(entries.size).toBe(0);
    });

    it('is removed by logout when only the namespace-only key exists', async () => {
        entries.set('foo', 'legacy.pat');
        await logout({ namespace: 'foo' });
        expect(entries.size).toBe(0);
    });

    it('is reported unknown by logout for a registry it was not stored for', async () => {
        entries.set('foo', 'legacy.pat');
        await expect(logout({ namespace: 'foo', registryUrl: 'http://127.0.0.1:3000' }))
            .rejects.toThrow("Unknown namespace 'foo'.");
        expect(entries.get('foo')).toBe('legacy.pat');
    });
});
