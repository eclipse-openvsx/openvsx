/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import fetchBuilder from 'fetch-retry';
import { isError } from './extension-registry-types';

export interface ServerAPIRequest {
    abortController?: AbortController;
    abortSignal?: AbortSignal;
    endpoint: string;
    method?: 'GET' | 'DELETE' | 'POST' | 'PUT';
    headers?: Record<string, string>;
    followRedirect?: boolean;
    credentials?: boolean;
    payload?: any;
}

export interface ErrorResponse {
    error: string;
    message: string;
    status: number;
    path?: string;
    timestamp?: string;
    trace?: string;
}

/**
 * RFC 9457 `application/problem+json`, which Spring returns for errors it handles itself rather
 * than through our `ResultJson` shape - a failed `@Min`/`@NotBlank` on a request parameter, say.
 * It carries none of {@link ErrorResponse}'s fields, so {@link handleError} has to read it too or
 * every such response reads as an unexplained failure.
 */
export interface ProblemDetail {
    type?: string;
    title?: string;
    detail?: string;
    status?: number;
    instance?: string;
    errors?: string[];
}

export async function sendRequest<Res>(req: ServerAPIRequest, retry: boolean = true): Promise<Res> {
    req.method ??= 'GET';
    req.headers ??= {};
    if (!req.headers['Accept']) {
        req.headers['Accept'] = 'application/json';
    }

    const param: RequestInit = {
        method: req.method
    };

    if (req.abortController) {
        param.signal = req.abortController.signal;
    } else if (req.abortSignal) {
        param.signal = req.abortSignal;
    }

    if (req.payload) {
        param.body =
            req.payload instanceof File || req.payload instanceof FormData ? req.payload : JSON.stringify(req.payload);
    }
    param.headers = req.headers;
    if (req.followRedirect) {
        param.redirect = 'follow';
    }
    if (req.credentials) {
        param.credentials = 'include';
    }

    const options: any = retry
        ? {
              retries: 10,
              retryDelay: (attempt: number, error: Error, response: Response) => {
                  return Math.pow(2, attempt) * 1000;
              },
              retryOn: (attempt: number, error: Error, response: Response) => {
                  return error !== null || response.status >= 500;
              }
          }
        : {};

    const response = await fetchBuilder(fetch, options)(req.endpoint, param);
    if (response.ok) {
        switch (req.headers['Accept']) {
            case 'application/json':
                return response.json();
            case 'text/plain':
                return response.text() as Promise<any>;
            case 'application/octet-stream':
                return response.blob() as Promise<any>;
            default:
                throw new Error(`Unsupported type ${req.headers['Accept']}`);
        }
    } else if (response.status === 429) {
        // if the server uses the bucket4j starter config
        const retryAfterSeconds = response.headers.get('X-Rate-Limit-Retry-After-Seconds');
        // if the server uses the dynamic rate limiting config
        const retryAfter = response.headers.get('Retry-After');
        const retrySeconds = retryAfterSeconds ?? retryAfter ?? '0';
        const jitter = Math.floor(Math.random() * 100);
        const timeoutMillis = (Number(retrySeconds) + 1) * 1000 + jitter;
        return new Promise<ServerAPIRequest>(resolve => setTimeout(resolve, timeoutMillis, req)).then(request =>
            sendRequest(request, retry)
        );
    } else {
        let err: ErrorResponse;
        try {
            err = await response.json();
            err.status = response.status;
        } catch (_) {
            err = {
                error: `Request failed: ${req.method} ${response.url}`,
                status: response.status,
                message: response.statusText || `Status ${response.status}`
            };
        }
        throw err;
    }
}

/**
 * Sends a non-retriable HTTP request using the {@link sendRequest} function with retries disabled.
 *
 * @template Res - The expected response type.
 * @param req - The {@link ServerAPIRequest} object containing the request configuration,
 *              including the endpoint, method, headers, payload, and other options.
 * @returns A promise that resolves to the response of type `Res`.
 */
export function sendNonRetriableRequest<Res>(req: ServerAPIRequest): Promise<Res> {
    return sendRequest<Res>(req, false);
}

/**
 * Like {@link sendNonRetriableRequest}, but also rejects when the server responds
 * 200 with an error result body. This lets TanStack query/mutation functions treat
 * the resolved value as plain data, without checking {@link isError} themselves.
 */
export async function sendStrictRequest<Res>(req: ServerAPIRequest): Promise<Res> {
    const result = await sendRequest<Res>(req, false);
    if (isError(result)) {
        throw result;
    }
    return result;
}
