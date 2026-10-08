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

import { Agent, fetch } from 'undici';
import { redactUrl } from './util';

// undici's own headers/body timeouts default to 5 minutes and would cap OVSX_TIMEOUT; the
// inactivity timer below is the only one that should apply. fetch and Agent come from the same
// undici, since a dispatcher is not guaranteed to work with the copy bundled in node.
const dispatcher = new Agent({ headersTimeout: 0, bodyTimeout: 0 });

export type RequestBody = string | Uint8Array | AsyncIterable<Uint8Array>;

export interface RequestOptions {
    method?: string;
    headers?: Record<string, string>;
    body?: RequestBody;
    redirect?: RequestRedirect;
    /** Inactivity timeout in milliseconds, see RegistryOptions.timeout. Zero disables it. */
    timeout: number;
}

/**
 * `fetch` with an inactivity timeout: every chunk sent or received resets the clock, so a slow but
 * progressing transfer is not cut off while a silent one fails with a timeout error. The returned
 * body must be consumed or cancelled, or the timer keeps running until it fires.
 */
export async function request(url: URL, options: RequestOptions): Promise<Response> {
    const controller = new AbortController();
    let timer: NodeJS.Timeout | undefined;
    const stop = () => clearTimeout(timer);
    const touch = () => {
        if (options.timeout > 0) {
            clearTimeout(timer);
            timer = setTimeout(() => {
                controller.abort(new Error(`No response from ${redactUrl(url)} for ${options.timeout} ms.`));
            }, options.timeout);
            // The pending request is what keeps the process alive, not its timer.
            timer.unref();
        }
    };

    const { body } = options;
    const streamed = body !== undefined && typeof body !== 'string' && !(body instanceof Uint8Array);
    touch();
    let response: Response;
    try {
        response = await fetch(url, {
            method: options.method,
            headers: options.headers,
            body: streamed ? trackProgress(body, touch) : body,
            redirect: options.redirect,
            signal: controller.signal,
            dispatcher,
            // required by fetch for a streamed request body
            ...(streamed ? { duplex: 'half' } : {})
        } as Parameters<typeof fetch>[1]) as unknown as Response;
    } catch (err) {
        stop();
        throw unwrapFetchError(err);
    }
    touch();

    if (!response.body) {
        stop();
        return response;
    }
    const reader = response.body.getReader();
    const tracked = new ReadableStream<Uint8Array>({
        async pull(streamController) {
            try {
                const { done, value } = await reader.read();
                if (done) {
                    stop();
                    streamController.close();
                } else {
                    touch();
                    streamController.enqueue(value);
                }
            } catch (err) {
                stop();
                streamController.error(unwrapFetchError(err));
            }
        },
        cancel(reason) {
            stop();
            return reader.cancel(reason);
        }
    });
    return new Response(tracked, {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers
    });
}

async function* trackProgress(body: AsyncIterable<Uint8Array>, touch: () => void): AsyncIterable<Uint8Array> {
    for await (const chunk of body) {
        touch();
        yield chunk;
    }
}

/**
 * fetch reports network failures as a bare `TypeError` ('fetch failed', 'terminated') and keeps
 * what actually happened, such as ECONNREFUSED or a dropped socket, in its cause. Some causes
 * carry no message at all, and an empty error says even less than 'fetch failed'.
 */
function unwrapFetchError(err: unknown): unknown {
    const cause = err instanceof TypeError ? (err as { cause?: unknown }).cause : undefined;
    return cause instanceof Error && cause.message ? cause : err;
}
