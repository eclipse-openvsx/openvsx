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
import { waitFor } from '@testing-library/react';
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
function renderNamespaceAdmin(user: UserData) {
    const getExtension = vi.fn().mockResolvedValue(barExtension);
    const getExtensionDetail = vi.fn().mockResolvedValue(barExtension);
    const service = {
        getExtensionDetail,
        getExtensionIcon: vi.fn().mockResolvedValue(null),
        getNamespaceDetails: vi.fn().mockResolvedValue(namespaceDetails()),
        getNamespaceMembers: vi.fn().mockResolvedValue({ namespaceMemberships: [] }),
        getTrustedPublishingStatus: vi.fn().mockResolvedValue({ enabled: false, allowed: false }),
        admin: {
            getNamespace: vi.fn().mockResolvedValue(testNamespace({ extensions: { bar: '/api/foo/bar' } })),
            getExtension
        }
    } as unknown as ExtensionRegistryService;

    renderWithProviders(
        <Routes>
            <Route path={AdminDashboardRoutes.NAMESPACE_ADMIN} element={<NamespaceAdmin />} />
            <Route path={`${AdminDashboardRoutes.NAMESPACE_ADMIN}/:namespace`} element={<NamespaceAdmin />} />
        </Routes>,
        { route: `${AdminDashboardRoutes.NAMESPACE_ADMIN}/foo`, mainContext: { service, user } }
    );
    return { getExtension, getExtensionDetail };
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
});
