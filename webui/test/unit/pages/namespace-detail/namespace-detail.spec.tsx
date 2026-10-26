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
import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { renderWithProviders } from '../../support/test-providers';
import { namespaceDetails } from '../../support/user-settings';
import { NamespaceDetail } from '../../../../src/pages/namespace-detail/namespace-detail';
import { NamespaceDetailRoutes } from '../../../../src/pages/namespace-detail/namespace-detail-routes';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';

function renderPage(getNamespaceDetails: ExtensionRegistryService['getNamespaceDetails']) {
    const handleError = vi.fn();
    renderWithProviders(
        <Routes>
            <Route path={NamespaceDetailRoutes.MAIN} element={<NamespaceDetail />} />
        </Routes>,
        {
            route: '/namespace/foo',
            mainContext: { service: { getNamespaceDetails } as unknown as ExtensionRegistryService, handleError }
        }
    );
    return { handleError };
}

describe('NamespaceDetail', () => {
    it('shows the details of the namespace in the URL', async () => {
        const getNamespaceDetails = vi.fn().mockResolvedValue(namespaceDetails({ displayName: 'Foo Corp' }));
        renderPage(getNamespaceDetails);

        expect(await screen.findByText('Foo Corp')).toBeInTheDocument();
        expect(getNamespaceDetails).toHaveBeenCalledWith(expect.any(AbortSignal), 'foo');
    });

    it('reports a missing namespace on the page instead of the error dialog', async () => {
        const { handleError } = renderPage(vi.fn().mockRejectedValue({ error: 'Not found', status: 404 }));

        expect(await screen.findByText('Namespace Not Found: foo')).toBeInTheDocument();
        expect(handleError).not.toHaveBeenCalled();
    });

    it('sends any other failure to the error dialog', async () => {
        const failure = { error: 'Server error', status: 500 };
        const { handleError } = renderPage(vi.fn().mockRejectedValue(failure));

        await waitFor(() => expect(handleError).toHaveBeenCalledWith(failure));
        expect(screen.queryByText('Namespace Not Found: foo')).not.toBeInTheDocument();
    });
});
