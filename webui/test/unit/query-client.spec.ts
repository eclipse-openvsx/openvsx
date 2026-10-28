/********************************************************************************
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
 ********************************************************************************/

import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { queryClient } from '../../src/query-client';

const { queries } = queryClient.getDefaultOptions();
const retry = queries?.retry as (failureCount: number, error: unknown) => boolean;
const retryDelay = queries?.retryDelay as (attempt: number, error: unknown) => number;

describe('queryClient retry policy', () => {
    it('retries a network error or a 5xx three times', () => {
        expect(retry(0, { status: 503 })).toBe(true);
        expect(retry(2, new TypeError('Failed to fetch'))).toBe(true);
        expect(retry(3, new TypeError('Failed to fetch'))).toBe(false);
    });

    it('does not retry a 4xx, or an error result the registry answered with', () => {
        expect(retry(0, { error: 'Forbidden', status: 403 })).toBe(false);
        expect(retry(0, { error: 'Not logged in.', status: 200 })).toBe(false);
    });

    it('waits out a 429 up to 10 times, for as long as the registry asks', () => {
        expect(retry(9, { status: 429 })).toBe(true);
        expect(retry(10, { status: 429 })).toBe(false);

        const delay = retryDelay(0, { status: 429, retryAfter: 5 });
        expect(delay).toBeGreaterThanOrEqual(6000);
        expect(delay).toBeLessThan(6100);
    });
});

describe('queryClient with a failing request', () => {
    const newClient = () => new QueryClient({ defaultOptions: queryClient.getDefaultOptions() });

    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('calls a query once when it fails with a 4xx', async () => {
        const queryFn = vi.fn().mockRejectedValue({ error: 'Forbidden', status: 403 });
        const observer = new QueryObserver(newClient(), { queryKey: ['forbidden'], queryFn });
        const unsubscribe = observer.subscribe(() => {});

        await vi.runAllTimersAsync();

        expect(queryFn).toHaveBeenCalledTimes(1);
        expect(observer.getCurrentResult().isError).toBe(true);
        unsubscribe();
    });

    it('retries a rate-limited query once the registry asked wait is over', async () => {
        const queryFn = vi.fn().mockRejectedValueOnce({ status: 429, retryAfter: 5 }).mockResolvedValue('done');
        const observer = new QueryObserver(newClient(), { queryKey: ['limited'], queryFn });
        const unsubscribe = observer.subscribe(() => {});

        await vi.advanceTimersByTimeAsync(5000);
        expect(queryFn).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(1200);
        expect(queryFn).toHaveBeenCalledTimes(2);
        expect(observer.getCurrentResult().data).toBe('done');
        unsubscribe();
    });

    it('calls a rate-limited mutation once', async () => {
        const mutationFn = vi.fn().mockRejectedValue({ status: 429, retryAfter: 1 });
        const settled = new MutationObserver(newClient(), { mutationFn }).mutate().catch(error => error);

        await vi.runAllTimersAsync();

        expect(await settled).toMatchObject({ status: 429 });
        expect(mutationFn).toHaveBeenCalledTimes(1);
    });
});
