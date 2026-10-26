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
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Registry } from '../../src/registry';
import { getTrustedPublishingToken, refreshTrustedPublishingToken } from '../../src/trusted-publishing';

interface FakeRegistry {
    url: string;
    exchanges: number;
}

// Answers the token exchange with the given statuses in order, the last one repeating.
async function startRegistry(...responses: Array<{ status: number; body: unknown }>): Promise<FakeRegistry> {
    const state: FakeRegistry = { url: '', exchanges: 0 };
    const server = http.createServer((req, res) => {
        const pathname = new URL(req.url ?? '/', 'http://127.0.0.1').pathname;
        req.resume();
        req.on('end', () => {
            if (pathname === '/api/version') {
                // The audience lookup rides along with the exchange rather than being one of the
                // responses this helper sequences, and reporting no audience keeps the registry URL.
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ version: '1.7.0' }));
                return;
            }
            const response = responses[Math.min(state.exchanges, responses.length - 1)];
            state.exchanges++;
            res.writeHead(response.status, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(response.body));
        });
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    state.url = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    servers.push(server);
    return state;
}

const servers: http.Server[] = [];

describe('trusted publishing token exchange', () => {
    beforeEach(() => {
        servers.length = 0;
    });

    afterEach(async () => {
        await Promise.all(servers.map(server => new Promise(resolve => server.close(resolve))));
    });

    // The registry answers 503 when it could not reach the identity provider, which says nothing about
    // the token. Failing a release build on a blip reaching GitHub would be the wrong call.
    it('retries the exchange when the registry could not verify the token', async () => {
        const registry = await startRegistry(
            { status: 503, body: { error: 'Could not verify the token with GitHub, please retry.' } },
            { status: 201, body: { value: 'the-access-token' } }
        );

        const token = await getTrustedPublishingToken(
            new Registry({ registryUrl: registry.url }),
            'retried',
            'ext',
            { idToken: 'an-id-token' }
        );

        expect(token).toBe('the-access-token');
        expect(registry.exchanges).toBe(2);
    });

    // A refusal is final: no registration matches, so trying again cannot help.
    it('does not retry when the registry refuses the token', async () => {
        const registry = await startRegistry({
            status: 403,
            body: { error: 'No trusted publisher matches the presented token.' }
        });

        await expect(
            getTrustedPublishingToken(new Registry({ registryUrl: registry.url }), 'refused', 'ext', {
                idToken: 'an-id-token'
            })
        ).rejects.toThrow('No trusted publisher matches the presented token.');

        expect(registry.exchanges).toBe(1);
    });

    // The issued token is short-lived and shared by every target platform of a release, so a slow
    // fan-out can outlive it. Refusing it must be answerable with a new one rather than failing a
    // release that was authorised.
    it('exchanges again when the token it was publishing with is refused', async () => {
        const registry = await startRegistry(
            { status: 201, body: { value: 'first-token' } },
            { status: 201, body: { value: 'second-token' } }
        );
        const options = { idToken: 'an-id-token' };
        const client = new Registry({ registryUrl: registry.url });

        const first = await getTrustedPublishingToken(client, 'expiring', 'ext', options);
        const second = await refreshTrustedPublishingToken(client, 'expiring', 'ext', options, first);

        expect(first).toBe('first-token');
        expect(second).toBe('second-token');
        expect(registry.exchanges).toBe(2);
        // and the replacement is what every later target platform picks up
        expect(await getTrustedPublishingToken(client, 'expiring', 'ext', options)).toBe('second-token');
    });

    // Target platforms publish concurrently, so they hit one expiry together. Exchanging per refusal
    // would ask the identity provider once per target and hand out tokens the others discard.
    it('exchanges once when every target platform is refused at the same time', async () => {
        const registry = await startRegistry(
            { status: 201, body: { value: 'first-token' } },
            { status: 201, body: { value: 'second-token' } }
        );
        const options = { idToken: 'an-id-token' };
        const client = new Registry({ registryUrl: registry.url });

        const stale = await getTrustedPublishingToken(client, 'concurrent', 'ext', options);
        const refreshed = await Promise.all(
            [0, 1, 2, 3].map(() =>
                refreshTrustedPublishingToken(client, 'concurrent', 'ext', options, stale))
        );

        expect(refreshed).toEqual(['second-token', 'second-token', 'second-token', 'second-token']);
        expect(registry.exchanges).toBe(2);
    });

    // A token already replaced is not stale twice: the caller that lost the race takes what is cached.
    it('does not exchange again for a token that was already replaced', async () => {
        const registry = await startRegistry(
            { status: 201, body: { value: 'first-token' } },
            { status: 201, body: { value: 'second-token' } },
            { status: 201, body: { value: 'third-token' } }
        );
        const options = { idToken: 'an-id-token' };
        const client = new Registry({ registryUrl: registry.url });

        const stale = await getTrustedPublishingToken(client, 'replaced', 'ext', options);
        await refreshTrustedPublishingToken(client, 'replaced', 'ext', options, stale);
        const again = await refreshTrustedPublishingToken(client, 'replaced', 'ext', options, stale);

        expect(again).toBe('second-token');
        expect(registry.exchanges).toBe(2);
    });
});

describe('trusted publishing audience', () => {

    const REQUEST_URL = 'ACTIONS_ID_TOKEN_REQUEST_URL';
    const REQUEST_TOKEN = 'ACTIONS_ID_TOKEN_REQUEST_TOKEN';

    interface PublishingRegistry {
        url: string;
        versionRequests: number;
    }

    interface IdTokenService {
        url: string;
        audiences: string[];
    }

    const servers: http.Server[] = [];
    const environment = { ...process.env };

    beforeEach(() => {
        delete process.env[REQUEST_URL];
        delete process.env[REQUEST_TOKEN];
    });

    afterEach(async () => {
        process.env = { ...environment };
        await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve()))));
    });

    /**
     * Stands in for a registry that accepts the exchange, answering `/api/version` with what is
     * given - absent means a registry that does not serve it at all.
     */
    async function startPublishingRegistry(version?: { status?: number; body?: unknown }): Promise<PublishingRegistry> {
        const registry: PublishingRegistry = { url: '', versionRequests: 0 };
        const server = http.createServer((req, res) => {
            const url = new URL(req.url ?? '/', 'http://127.0.0.1');
            req.resume();
            req.on('end', () => {
                if (url.pathname === '/api/version') {
                    registry.versionRequests++;
                    if (version) {
                        res.writeHead(version.status ?? 200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify(version.body));
                    } else {
                        res.writeHead(404, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Not found' }));
                    }
                } else {
                    res.writeHead(201, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ value: 'the-access-token' }));
                }
            });
        });
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        registry.url = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
        servers.push(server);
        return registry;
    }

    /** Stands in for the ID token service of a CI system, recording the audience it is asked for. */
    async function startIdTokenService(): Promise<IdTokenService> {
        const service: IdTokenService = { url: '', audiences: [] };
        const server = http.createServer((req, res) => {
            const url = new URL(req.url ?? '/', 'http://127.0.0.1');
            service.audiences.push(url.searchParams.get('audience') ?? '');
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ count: 1, value: 'the.id.token' }));
        });
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        service.url = `http://127.0.0.1:${(server.address() as AddressInfo).port}/token`;
        servers.push(server);
        process.env[REQUEST_URL] = service.url;
        process.env[REQUEST_TOKEN] = 'runner-token';
        return service;
    }

    async function exchangedAudience(
        registry: PublishingRegistry,
        namespace: string,
        options: { oidcAudience?: string } = {}
    ): Promise<string[]> {
        const idTokens = await startIdTokenService();
        await expect(getTrustedPublishingToken(new Registry({ registryUrl: registry.url }), namespace, 'ext', options))
            .resolves.toBe('the-access-token');
        return idTokens.audiences;
    }

    // The audience a registry validates is its own configuration: ovsx.trusted-publishing.audience
    // defaults to the URL its web UI is served from, which need not be the API URL the CLI is pointed
    // at. Minting the ID token for the wrong one is refused as an audience mismatch, which looks like
    // a trusted publisher that is not registered.
    it('asks for the audience the registry reports', async () => {
        const registry = await startPublishingRegistry({
            body: { version: '1.7.0', trustedPublishingAudience: 'https://ui.example.org' }
        });

        expect(await exchangedAudience(registry, 'reported')).toEqual(['https://ui.example.org']);
        expect(registry.versionRequests).toBe(1);
    });

    // Trusted publishing that is switched off reports no audience, and neither does a registry that
    // does not serve /api/version at all. Both keep the registry URL, which is what the CLI used
    // before and what a deployment with one host for UI and API expects.
    it('keeps the registry URL when the registry reports no audience', async () => {
        const registry = await startPublishingRegistry({ body: { version: '1.7.0', maxExtensionSize: 100 } });

        expect(await exchangedAudience(registry, 'unreported')).toEqual([registry.url]);
    });

    it('keeps the registry URL when the registry does not serve /api/version', async () => {
        const registry = await startPublishingRegistry();

        expect(await exchangedAudience(registry, 'versionless')).toEqual([registry.url]);
    });

    // The ID token is a bearer credential and the registry picks its audience, so neither is sent over
    // plain http. Nothing is minted or requested before that is checked.
    it('refuses a registry that is not reached over https', async () => {
        const idTokens = await startIdTokenService();

        await expect(getTrustedPublishingToken(
            new Registry({ registryUrl: 'http://registry.example.org' }), 'insecure', 'ext', {}
        )).rejects.toThrow('the registry URL must use https, but it is http://registry.example.org/');

        expect(idTokens.audiences).toEqual([]);
    });

    // An explicit audience is what the caller asked for, so the registry is not asked.
    it('prefers an explicit audience without consulting the registry', async () => {
        const registry = await startPublishingRegistry({
            body: { version: '1.7.0', trustedPublishingAudience: 'https://ui.example.org' }
        });

        expect(await exchangedAudience(registry, 'explicit', { oidcAudience: 'https://explicit.example.org' }))
            .toEqual(['https://explicit.example.org']);
        expect(registry.versionRequests).toBe(0);
    });
});
