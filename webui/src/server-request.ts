/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { isError } from './extension-registry-types';

export interface ServerAPIRequest {
    signal?: AbortSignal;
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
    /** On a 429, the seconds the registry asks to wait before the request is sent again. */
    retryAfter?: number;
}

/** Whether a rejection is the registry's 404, for a page that renders "not found" itself. */
export const isNotFound = (error: unknown): boolean => (error as Partial<ErrorResponse> | null)?.status === 404;

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

/**
 * Resolves with the parsed body of a 2xx response and rejects with an {@link ErrorResponse}
 * otherwise, including a 200 whose body is an error result, so a caller never checks
 * {@link isError} itself. It never retries: the query client owns retries, a 429 included.
 */
export async function sendRequest<Res>(req: ServerAPIRequest): Promise<Res> {
    req.method ??= 'GET';
    req.headers ??= {};
    if (!req.headers['Accept']) {
        req.headers['Accept'] = 'application/json';
    }

    const param: RequestInit = {
        method: req.method,
        signal: req.signal
    };

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

    const response = await fetch(req.endpoint, param);
    if (response.ok) {
        const body = await parseBody(response, req.headers['Accept']);
        if (isError(body)) {
            // Carries its status like any other failure, so the query client won't retry an answer.
            throw { ...body, status: response.status };
        }
        return body;
    }

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
    if (response.status === 429) {
        // The bucket4j starter config sends the first header, dynamic rate limiting the second.
        const retryAfter =
            response.headers.get('X-Rate-Limit-Retry-After-Seconds') ?? response.headers.get('Retry-After');
        err.retryAfter = Number(retryAfter) || 0;
    }
    throw err;
}

function parseBody(response: Response, accept: string): Promise<any> {
    switch (accept) {
        case 'application/json':
            return response.json();
        case 'text/plain':
            return response.text();
        case 'application/octet-stream':
            return response.blob();
        default:
            throw new Error(`Unsupported type ${accept}`);
    }
}
