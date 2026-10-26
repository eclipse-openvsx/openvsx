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
import { renderHookWithProviders } from '../../support/test-providers';
import { namespaceDetails } from '../../support/user-settings';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import {
    useNamespaceDetails,
    useUpdateNamespaceDetails,
    useUpdateNamespaceLogo
} from '../../../../src/components/namespace/use-namespace-details';

function detailsServiceStub() {
    const getNamespaceDetails = vi.fn().mockResolvedValue(namespaceDetails());
    const setNamespaceDetails = vi.fn().mockResolvedValue(undefined);
    const setNamespaceLogo = vi.fn().mockResolvedValue(undefined);
    return {
        getNamespaceDetails,
        setNamespaceDetails,
        setNamespaceLogo,
        service: {
            getNamespaceDetails,
            setNamespaceDetails,
            setNamespaceLogo
        } as unknown as ExtensionRegistryService
    };
}

describe('useNamespaceDetails', () => {
    it('loads the namespace details', async () => {
        const { getNamespaceDetails, service } = detailsServiceStub();
        const { result } = renderHookWithProviders(() => useNamespaceDetails('foo'), { mainContext: { service } });

        await waitFor(() => expect(result.current.data?.name).toBe('foo'));
        expect(getNamespaceDetails).toHaveBeenCalledWith(expect.any(AbortSignal), 'foo');
    });
});

describe('namespace details mutations', () => {
    it('saving the details refreshes what the form and the logo read', async () => {
        const { getNamespaceDetails, setNamespaceDetails, service } = detailsServiceStub();
        const { result } = renderHookWithProviders(
            () => ({ details: useNamespaceDetails('foo'), update: useUpdateNamespaceDetails() }),
            { mainContext: { service } }
        );
        await waitFor(() => expect(getNamespaceDetails).toHaveBeenCalledTimes(1));

        await act(() =>
            result.current.update.mutateAsync({ detailsUrl: '/details', details: namespaceDetails({ name: 'foo' }) })
        );

        expect(setNamespaceDetails).toHaveBeenCalledWith('/details', expect.anything());
        await waitFor(() => expect(getNamespaceDetails).toHaveBeenCalledTimes(2));
    });

    it('uploading a logo refreshes the same details entry', async () => {
        const { getNamespaceDetails, setNamespaceLogo, service } = detailsServiceStub();
        const { result } = renderHookWithProviders(
            () => ({ details: useNamespaceDetails('foo'), upload: useUpdateNamespaceLogo() }),
            { mainContext: { service } }
        );
        await waitFor(() => expect(getNamespaceDetails).toHaveBeenCalledTimes(1));

        const file = new Blob(['logo'], { type: 'image/png' });
        await act(() =>
            result.current.upload.mutateAsync({
                namespaceName: 'foo',
                detailsUrl: '/details',
                file,
                fileName: 'logo.png'
            })
        );

        expect(setNamespaceLogo).toHaveBeenCalledWith('/details', file, 'logo.png');
        await waitFor(() => expect(getNamespaceDetails).toHaveBeenCalledTimes(2));
    });
});
