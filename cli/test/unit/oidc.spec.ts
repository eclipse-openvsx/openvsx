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
import { getIdToken, hasIdTokenSource } from '../../src/oidc';
import { useTrustedPublishing } from '../../src/trusted-publishing';

const REQUEST_URL = 'ACTIONS_ID_TOKEN_REQUEST_URL';
const REQUEST_TOKEN = 'ACTIONS_ID_TOKEN_REQUEST_TOKEN';

interface TokenServiceRequest {
    query: URLSearchParams;
    authorization?: string;
}

interface TokenService {
    url: string;
    requests: TokenServiceRequest[];
    close: () => Promise<void>;
}

/**
 * Stands in for the ID token service of a CI system, e.g. the GitHub Actions workflow runtime.
 */
async function startTokenService(status: number, body: unknown): Promise<TokenService> {
    const requests: TokenServiceRequest[] = [];
    const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', 'http://127.0.0.1');
        requests.push({ query: url.searchParams, authorization: req.headers['authorization'] });
        res.writeHead(status, { 'Content-Type': 'application/json' });
        res.end(typeof body === 'string' ? body : JSON.stringify(body));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}/token?api-version=2.0`,
        requests,
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

/** Stands in for a token service that accepts the connection and then says nothing at all. */
async function startSilentTokenService(): Promise<TokenService> {
    const server = http.createServer(() => { /* deliberately never responds */ });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}/token?api-version=2.0`,
        requests: [],
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

/**
 * Stands in for a token service whose connection dies once it has started answering, which is the
 * case where the response's own error is the only thing that can settle the promise.
 */
async function startStallingTokenService(): Promise<TokenService> {
    const server = http.createServer((_, res) => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.write('{"count":1');
        setTimeout(() => res.socket?.destroy(), 30);
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    return {
        url: `http://127.0.0.1:${port}/token?api-version=2.0`,
        requests: [],
        close: () => new Promise<void>(resolve => server.close(() => resolve()))
    };
}

describe('OIDC ID token detection', () => {

    const environment = { ...process.env };
    const services: TokenService[] = [];

    beforeEach(() => {
        delete process.env[REQUEST_URL];
        delete process.env[REQUEST_TOKEN];
    });

    afterEach(async () => {
        process.env = { ...environment };
        await Promise.all(services.splice(0).map(service => service.close()));
    });

    async function givenTokenService(status: number = 200, body: unknown = { count: 1, value: 'the.id.token' }) {
        const service = await startTokenService(status, body);
        services.push(service);
        process.env[REQUEST_URL] = service.url;
        process.env[REQUEST_TOKEN] = 'runner-token';
        return service;
    }

    describe('hasIdTokenSource', () => {
        it('detects an explicitly provided ID token', () => {
            expect(hasIdTokenSource({ idToken: 'the.id.token' })).toBe(true);
        });

        it('detects the GitHub Actions workflow runtime', () => {
            process.env[REQUEST_URL] = 'https://example.org/token?api-version=2.0';
            process.env[REQUEST_TOKEN] = 'runner-token';
            expect(hasIdTokenSource({})).toBe(true);
        });

        it('is not fooled by a partial GitHub Actions environment', () => {
            process.env[REQUEST_URL] = 'https://example.org/token?api-version=2.0';
            expect(hasIdTokenSource({})).toBe(false);

            delete process.env[REQUEST_URL];
            process.env[REQUEST_TOKEN] = 'runner-token';
            expect(hasIdTokenSource({})).toBe(false);
        });

        it('reports no source outside of a CI system', () => {
            expect(hasIdTokenSource({})).toBe(false);
        });
    });

    describe('getIdToken', () => {
        it('returns an explicitly provided ID token', async () => {
            const service = await givenTokenService();
            await expect(getIdToken('https://open-vsx.org', { idToken: 'explicit.id.token' }))
                .resolves.toBe('explicit.id.token');
            // an ID token at hand takes precedence, the token service must not be contacted
            expect(service.requests).toHaveLength(0);
        });

        it('requests an ID token from the GitHub Actions workflow runtime', async () => {
            const service = await givenTokenService();

            await expect(getIdToken('https://open-vsx.org', {})).resolves.toBe('the.id.token');

            expect(service.requests).toHaveLength(1);
            const request = service.requests[0];
            expect(request.query.get('audience')).toBe('https://open-vsx.org');
            // the request URL of the runtime carries a query already, which must be preserved
            expect(request.query.get('api-version')).toBe('2.0');
            expect(request.authorization).toBe('Bearer runner-token');
        });

        it('fails if the workflow runtime returns no ID token', async () => {
            await givenTokenService(200, { count: 0 });
            await expect(getIdToken('https://open-vsx.org', {}))
                .rejects.toThrow('GitHub Actions did not return an OIDC ID token.');
        });

        it('fails if the workflow runtime rejects the request', async () => {
            await givenTokenService(403, { message: 'no id-token permission' });
            await expect(getIdToken('https://open-vsx.org', {})).rejects.toThrow(/status 403/);
        });

        // The response had no error listener, so a token service that died part-way through answering
        // left trusted publishing waiting on a body that was not coming rather than failing.
        it('fails if the workflow runtime drops the connection mid-response', async () => {
            const service = await startStallingTokenService();
            services.push(service);
            process.env[REQUEST_URL] = service.url;
            process.env[REQUEST_TOKEN] = 'runner-token';

            const outcome = await Promise.race([
                getIdToken('https://open-vsx.org', {}).then(() => 'resolved', (err: Error) => `rejected: ${err.message}`),
                new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
            ]);

            expect(outcome).toContain('rejected');
            expect(outcome).not.toBe('never settled');
        });

        // This is a second HTTP implementation, so the registry's timeout tests cannot cover it: a
        // silent token service would hang trusted publishing with nothing to notice. Distinct from the
        // mid-response test above, which the response's error handler settles either way and which
        // therefore passes with this timeout removed.
        it('fails if the workflow runtime accepts the connection and never responds', async () => {
            const service = await startSilentTokenService();
            services.push(service);
            process.env[REQUEST_URL] = service.url;
            process.env[REQUEST_TOKEN] = 'runner-token';
            process.env['OVSX_TIMEOUT'] = '200';

            const outcome = await Promise.race([
                getIdToken('https://open-vsx.org', {}).then(() => 'resolved', (err: Error) => err.message),
                new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
            ]);

            expect(outcome).toContain('No response from');
            expect(outcome).not.toContain('token=');
        });

        it('fails with an actionable message if no ID token is available', async () => {
            await expect(getIdToken('https://open-vsx.org', {}))
                .rejects.toThrow(/id-token: write/);
        });
    });

    describe('useTrustedPublishing', () => {
        it('follows an explicit decision', () => {
            expect(useTrustedPublishing({ trustedPublishing: true })).toBe(true);
            process.env[REQUEST_URL] = 'https://example.org/token?api-version=2.0';
            process.env[REQUEST_TOKEN] = 'runner-token';
            expect(useTrustedPublishing({ trustedPublishing: false })).toBe(false);
        });

        it('falls back to the detected ID token source', () => {
            expect(useTrustedPublishing({})).toBe(false);
            process.env[REQUEST_URL] = 'https://example.org/token?api-version=2.0';
            process.env[REQUEST_TOKEN] = 'runner-token';
            expect(useTrustedPublishing({})).toBe(true);
        });
    });
});
