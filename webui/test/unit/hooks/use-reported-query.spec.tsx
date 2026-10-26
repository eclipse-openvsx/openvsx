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

import { describe, expect, it, vi } from 'vitest';
import { act, waitFor } from '@testing-library/react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { renderHookWithProviders } from '../support/test-providers';
import { useReportedQuery } from '../../../src/hooks/use-reported-query';
import { isNotFound } from '../../../src/server-request';

const failingWith = (status: number) => () => Promise.reject(Object.assign(new Error(`status ${status}`), { status }));

describe('useReportedQuery', () => {
    it('reports a failed query', async () => {
        const handleError = vi.fn();
        renderHookWithProviders(() => useReportedQuery(useQuery({ queryKey: ['q'], queryFn: failingWith(500) })), {
            mainContext: { handleError }
        });

        await waitFor(() => expect(handleError).toHaveBeenCalledWith(expect.objectContaining({ status: 500 })));
    });

    it('leaves out an error the page renders itself', async () => {
        const handleError = vi.fn();
        const { result } = renderHookWithProviders(
            () => useReportedQuery(useQuery({ queryKey: ['q'], queryFn: failingWith(404) }), { ignore: isNotFound }),
            { mainContext: { handleError } }
        );

        await waitFor(() => expect(result.current.isError).toBe(true));
        expect(handleError).not.toHaveBeenCalled();
    });

    it('reports a failed mutation', async () => {
        const handleError = vi.fn();
        const { result } = renderHookWithProviders(
            () => useReportedQuery(useMutation({ mutationFn: failingWith(403) })),
            { mainContext: { handleError } }
        );

        act(() => result.current.mutate());

        await waitFor(() => expect(handleError).toHaveBeenCalledWith(expect.objectContaining({ status: 403 })));
    });
});
