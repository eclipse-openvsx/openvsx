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
import { waitFor } from '@testing-library/react';
import { createTestQueryClient, renderHookWithProviders } from '../../support/test-providers';
import { extensionDetailKeys, useExtensionDetail } from '../../../../src/pages/extension-detail/use-extension-details';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension } from '../../../../src/extension-registry-types';

describe('useExtensionDetail', () => {
    it('refetches an extension cached earlier, even under the app default of a fresh minute', async () => {
        const cached = { namespace: 'foo', name: 'bar', version: '1.0.0' } as Extension;
        const current = { ...cached, version: '2.0.0' } as Extension;
        const getExtensionDetail = vi.fn().mockResolvedValue(current);
        const queryClient = createTestQueryClient();
        queryClient.setDefaultOptions({ queries: { retry: false, gcTime: Infinity, staleTime: 60_000 } });
        queryClient.setQueryData(extensionDetailKeys.detail('foo', 'bar'), cached);

        const { result } = renderHookWithProviders(() => useExtensionDetail('foo', 'bar'), {
            queryClient,
            mainContext: {
                service: {
                    getExtensionDetail,
                    getExtensionApiUrl: () => 'https://registry.test/api/foo/bar'
                } as unknown as ExtensionRegistryService
            }
        });

        await waitFor(() => expect(result.current.data?.version).toBe('2.0.0'));
        expect(getExtensionDetail).toHaveBeenCalledTimes(1);
    });
});
