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

import { QueryClient } from '@tanstack/react-query';
import type { ErrorResponse } from './server-request';

/** Caps the waits for a 429 to clear, so a registry that keeps rate limiting eventually surfaces it. */
const MAX_RATE_LIMIT_RETRIES = 10;

const statusOf = (error: unknown) => (error as Partial<ErrorResponse> | null)?.status;

// Waits as long as the registry asked, with a jitter so throttled clients don't come back in lockstep.
const rateLimitDelay = (error: unknown) =>
    ((error as Partial<ErrorResponse>).retryAfter ?? 0) * 1000 + 1000 + Math.floor(Math.random() * 100);

// Shared singleton query client for the whole app, created once at module scope so the
// cache survives re-renders. It owns every retry: `sendRequest` never retries itself, and
// mutations keep TanStack's default of none, a 429 included.
export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            refetchOnWindowFocus: false,
            staleTime: 60 * 1000,
            // Network errors and 5xx are retried with exponential backoff and a 429 is waited out,
            // but no other 4xx is retried: it won't recover.
            retry: (failureCount, error) => {
                const status = statusOf(error);
                if (status === 429) {
                    return failureCount < MAX_RATE_LIMIT_RETRIES;
                }
                if (status !== undefined && status < 500) {
                    return false;
                }
                return failureCount < 3;
            },
            retryDelay: (attempt, error) => {
                if (statusOf(error) === 429) {
                    return rateLimitDelay(error);
                }
                const backoff = Math.min(1000 * 2 ** attempt, 30000);
                return Math.round(backoff / 2 + Math.random() * (backoff / 2));
            }
        }
    }
});

// Extension icons are cached as object URLs; revoke them when their query is
// evicted from the cache so the underlying blobs are freed.
queryClient.getQueryCache().subscribe(event => {
    if (event.type === 'removed' && event.query.queryKey[0] === 'extension-icon') {
        const url = event.query.state.data;
        if (typeof url === 'string' && url.startsWith('blob:')) {
            URL.revokeObjectURL(url);
        }
    }
});

/**
 * Query options disabling caching entirely, for data that must always reflect
 * the latest server state (e.g. right after publishing or deleting).
 */
export const NO_CACHE = { staleTime: 0, gcTime: 0 } as const;
