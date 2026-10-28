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
import { renderHookWithProviders } from '../../../support/test-providers';
import {
    useAllTrustedPublishers,
    useDeleteTrustedPublisher,
    useRegisterTrustedPublisher,
    useTrustedPublishers,
    useTrustedPublishingStatus
} from '../../../../../src/pages/user/trusted-publishing/use-trusted-publishers';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import {
    enabledStatus,
    statusServiceStub,
    testUser,
    trustedPublisher,
    trustedPublisherList
} from '../../../support/trusted-publishing';

const URL_FOO = '/user/namespace/foo/trusted-publishing';
const URL_BAR = '/user/namespace/bar/trusted-publishing';

describe('useTrustedPublishingStatus', () => {
    // The endpoint answers 403 to anonymous requests, so the query must idle
    // without a user — and the undefined data keeps feature gates closed.
    it('idles without a logged-in user', () => {
        const { getTrustedPublishingStatus, service } = statusServiceStub();
        const { result } = renderHookWithProviders(() => useTrustedPublishingStatus(), { mainContext: { service } });

        expect(result.current.fetchStatus).toBe('idle');
        expect(result.current.data).toBeUndefined();
        expect(getTrustedPublishingStatus).not.toHaveBeenCalled();
    });

    it('fetches the status for a logged-in user', async () => {
        const { service } = statusServiceStub();
        const { result } = renderHookWithProviders(() => useTrustedPublishingStatus(), {
            mainContext: { service, user: testUser }
        });

        await waitFor(() => expect(result.current.data).toEqual(enabledStatus));
    });
});

describe('useTrustedPublishers', () => {
    it('idles without a trusted-publishing URL', () => {
        const getTrustedPublishers = vi.fn();
        const service = { getTrustedPublishers } as unknown as ExtensionRegistryService;
        const { result } = renderHookWithProviders(() => useTrustedPublishers(undefined), {
            mainContext: { service, user: testUser }
        });

        expect(result.current.fetchStatus).toBe('idle');
        expect(getTrustedPublishers).not.toHaveBeenCalled();
    });
});

describe('useAllTrustedPublishers', () => {
    it('flattens the lists of all namespaces and skips failed ones', async () => {
        const fooPublisher = trustedPublisher({ id: 1, namespace: 'foo' });
        const getTrustedPublishers = vi.fn().mockImplementation((_signal: AbortSignal, url: string) =>
            url === URL_FOO
                ? Promise.resolve(
                      trustedPublisherList({
                          trustedPublishers: [fooPublisher],
                          registrableExtensions: ['baz']
                      })
                  )
                : Promise.reject({ error: 'Forbidden', status: 403 })
        );
        const service = { getTrustedPublishers } as unknown as ExtensionRegistryService;
        const { result } = renderHookWithProviders(() => useAllTrustedPublishers([URL_FOO, URL_BAR]), {
            mainContext: { service, user: testUser }
        });

        await waitFor(() => expect(result.current.isLoading).toBe(false));
        expect(result.current.publishers).toEqual([fooPublisher]);
        // stays index-aligned with the requested URLs, so a failed namespace registers as nothing registrable
        expect(result.current.registrableExtensions).toEqual([['baz'], []]);
    });
});

describe('trusted publisher mutations', () => {
    function serviceStub() {
        const getTrustedPublishers = vi.fn().mockResolvedValue(trustedPublisherList());
        const registerTrustedPublisher = vi.fn().mockResolvedValue(trustedPublisher());
        const deleteTrustedPublisher = vi.fn().mockResolvedValue({ success: 'ok' });
        return {
            getTrustedPublishers,
            registerTrustedPublisher,
            deleteTrustedPublisher,
            service: {
                getTrustedPublishers,
                registerTrustedPublisher,
                deleteTrustedPublisher
            } as unknown as ExtensionRegistryService
        };
    }

    it('registering refreshes the publishers of the target namespace', async () => {
        const { getTrustedPublishers, registerTrustedPublisher, service } = serviceStub();
        const request = { provider: 'github', namespace: 'foo', extension: 'bar', registration: { owner: 'octo' } };
        const { result } = renderHookWithProviders(
            () => ({ publishers: useTrustedPublishers(URL_FOO), register: useRegisterTrustedPublisher() }),
            { mainContext: { service, user: testUser } }
        );
        await waitFor(() => expect(result.current.publishers.isSuccess).toBe(true));

        await act(async () => {
            await result.current.register.mutateAsync({ trustedPublishingUrl: URL_FOO, request });
        });

        expect(registerTrustedPublisher).toHaveBeenCalledWith(URL_FOO, request);
        await waitFor(() => expect(getTrustedPublishers).toHaveBeenCalledTimes(2));
    });

    it('deleting refreshes the publishers of the target namespace', async () => {
        const { getTrustedPublishers, deleteTrustedPublisher, service } = serviceStub();
        const { result } = renderHookWithProviders(
            () => ({ publishers: useTrustedPublishers(URL_FOO), remove: useDeleteTrustedPublisher() }),
            { mainContext: { service, user: testUser } }
        );
        await waitFor(() => expect(result.current.publishers.isSuccess).toBe(true));

        await act(async () => {
            await result.current.remove.mutateAsync({ trustedPublishingUrl: URL_FOO, id: 1 });
        });

        expect(deleteTrustedPublisher).toHaveBeenCalledWith(URL_FOO, 1);
        await waitFor(() => expect(getTrustedPublishers).toHaveBeenCalledTimes(2));
    });
});
