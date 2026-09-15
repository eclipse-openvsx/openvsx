/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, it, expect, afterEach } from 'vitest';
import * as http from 'node:http';
import { AddressInfo } from 'node:net';
import { Registry } from '../../src/registry';

describe('Registry JSON requests', () => {

    const servers: http.Server[] = [];

    afterEach(async () => {
        for (const server of servers.splice(0)) {
            await new Promise<void>(resolve => server.close(() => resolve()));
        }
    });

    async function serve(handler: http.RequestListener): Promise<string> {
        const server = http.createServer(handler);
        servers.push(server);
        await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
        return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    }

    // A connection lost after the headers have arrived never fires 'end' on the response, and the
    // response's own error had no listener - so the promise was never settled and the command waited
    // on a body that was not coming. Asserted with the timeout switched off, since a timeout would
    // otherwise mask the hang by eventually rejecting for the wrong reason.
    it('rejects rather than hanging when the connection drops mid-response', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.write('{"namespace":"foo"');
            setTimeout(() => res.socket?.destroy(), 30);
        });
        const registry = new Registry({ registryUrl: url, timeout: 0 });

        const outcome = await Promise.race([
            registry.getJson(new URL(`${url}/api/foo`)).then(
                () => 'resolved',
                (err: NodeJS.ErrnoException) => `rejected: ${err.code ?? err.message}`
            ),
            new Promise<string>(resolve => setTimeout(() => resolve('never settled'), 3000))
        ]);

        expect(outcome).toBe('rejected: ECONNRESET');
    });

    // The timeout can also fire once part of the body has arrived. The request's error has to be what
    // settles the promise there, so the message says what happened rather than reporting a reset.
    it('reports a stalled response as a timeout, not as a reset', async () => {
        const url = await serve((_, res) => {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.write('{"namespace":"foo"');
        });
        const registry = new Registry({ registryUrl: url, timeout: 200 });

        await expect(registry.getJson(new URL(`${url}/api/foo`))).rejects.toThrow('No response from');
    });
});
