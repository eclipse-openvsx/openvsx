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

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { renderWithProviders } from '../../support/test-providers';
import { namespaceDetails, testNamespace } from '../../support/user-settings';
import { NamespaceAdmin } from '../../../../src/pages/admin-dashboard/namespace-admin';
import { AdminDashboardRoutes } from '../../../../src/pages/admin-dashboard/admin-dashboard-routes';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, UserData } from '../../../../src/extension-registry-types';

const barExtension = {
    name: 'bar',
    namespace: 'foo',
    version: '1.0.0',
    displayName: 'Bar',
    files: {},
    downloadCount: 0,
    reviewCount: 0,
    deprecated: false
} as Extension;

const namespaceManager: UserData = {
    loginName: 'nina',
    tokensUrl: '',
    createTokenUrl: '',
    permissions: ['manage_namespaces']
};

// NamespaceAdmin is mounted under these nested routes by admin-dashboard.tsx; reproduce that here
// so useParams()/navigate() behave as they do in the app.
// What the public API answers for an extension it has nothing to return: a 404, as sendRequest
// shapes it. A network failure or a 5xx is a different thing entirely and must not be swallowed.
const notFound = { error: 'Not found', status: 404 };

function renderNamespaceAdmin(
    user: UserData,
    extensions: Record<string, string> = { bar: '/api/foo/bar' },
    rejection: unknown = notFound
) {
    const getExtension = vi.fn().mockResolvedValue(barExtension);
    // Resolves whatever the public API would have: anything else is an extension it cannot see.
    const getExtensionDetail = vi.fn(async (_abort: AbortController, url: string) =>
        url === '/api/foo/bar' ? barExtension : Promise.reject(rejection)
    );
    const handleError = vi.fn();
    const service = {
        getExtensionDetail,
        getExtensionIcon: vi.fn().mockResolvedValue(null),
        getNamespaceDetails: vi.fn().mockResolvedValue(namespaceDetails()),
        getNamespaceMembers: vi.fn().mockResolvedValue({ namespaceMemberships: [] }),
        getTrustedPublishingStatus: vi.fn().mockResolvedValue({ enabled: false, allowed: false }),
        admin: {
            getNamespace: vi.fn().mockResolvedValue(testNamespace({ extensions })),
            getExtension
        }
    } as unknown as ExtensionRegistryService;

    renderWithProviders(
        <Routes>
            <Route path={AdminDashboardRoutes.NAMESPACE_ADMIN} element={<NamespaceAdmin />} />
            <Route path={`${AdminDashboardRoutes.NAMESPACE_ADMIN}/:namespace`} element={<NamespaceAdmin />} />
        </Routes>,
        { route: `${AdminDashboardRoutes.NAMESPACE_ADMIN}/foo`, mainContext: { service, user, handleError } }
    );
    return { getExtension, getExtensionDetail, handleError };
}

describe('NamespaceAdmin', () => {
    // The admin extension endpoint requires manage_extensions of its own. Calling it regardless
    // would 403 once per extension and leave the namespace looking like it has none.
    it('loads extensions from the public API for a user who only manages namespaces', async () => {
        const { getExtension, getExtensionDetail } = renderNamespaceAdmin(namespaceManager);

        await waitFor(() => expect(getExtensionDetail).toHaveBeenCalled());
        expect(getExtension).not.toHaveBeenCalled();
    });

    it('loads extensions from the admin API once the user can manage extensions', async () => {
        const { getExtension, getExtensionDetail } = renderNamespaceAdmin({
            ...namespaceManager,
            permissions: ['manage_namespaces', 'manage_extensions']
        });

        await waitFor(() => expect(getExtension).toHaveBeenCalled());
        expect(getExtensionDetail).not.toHaveBeenCalled();
    });

    // The admin namespace payload names inactive and soft-deleted extensions too, which the public
    // API has nothing to return for - an absence for this viewer, not a failure worth a dialog.
    it('leaves out an extension the public API cannot resolve, without reporting it', async () => {
        const { getExtensionDetail, handleError } = renderNamespaceAdmin(namespaceManager, {
            bar: '/api/foo/bar',
            quarantined: '/api/foo/quarantined'
        });

        await waitFor(() => expect(getExtensionDetail).toHaveBeenCalledTimes(2));
        expect(await screen.findByText('Bar')).toBeInTheDocument();
        expect(handleError).not.toHaveBeenCalled();
    });

    // Suppressing every failure instead would let a network error or a 5xx drop cards silently,
    // leaving a partial list looking like the whole namespace.
    it('still reports a failure that is not the extension being absent', async () => {
        const { getExtensionDetail, handleError } = renderNamespaceAdmin(
            namespaceManager,
            { bar: '/api/foo/bar', flaky: '/api/foo/flaky' },
            { error: 'Service Unavailable', status: 503 }
        );

        await waitFor(() => expect(getExtensionDetail).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(handleError).toHaveBeenCalled());
    });
});
