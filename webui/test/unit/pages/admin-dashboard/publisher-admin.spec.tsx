/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { renderWithProviders } from '../../support/test-providers';
import { PublisherAdmin } from '../../../../src/pages/admin-dashboard/publisher-admin';
import { AdminDashboardRoutes } from '../../../../src/pages/admin-dashboard/admin-dashboard-routes';
import { ExtensionRegistryService, AdminService } from '../../../../src/extension-registry-service';
import { PublisherInfo, UserSearchResult } from '../../../../src/extension-registry-types';

function serviceWith(forgetUser: AdminService['forgetUser']): ExtensionRegistryService {
    const users: UserSearchResult = {
        content: [
            { user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' }, namespaces: [] }
        ],
        page: { number: 0, size: 25, totalElements: 1, totalPages: 1 }
    } as unknown as UserSearchResult;

    const publisherInfo: PublisherInfo = {
        user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' },
        extensions: [],
        activeAccessTokenNum: 0
    };

    const admin = {
        getUsers: vi.fn().mockResolvedValue(users),
        getPublisherInfo: vi.fn().mockResolvedValue(publisherInfo),
        forgetUser
    } as unknown as AdminService;

    return { serverUrl: 'https://open-vsx.org', admin } as ExtensionRegistryService;
}

// PublisherAdmin is normally mounted under this nested route by admin-dashboard.tsx; reproduce
// that here so useParams()/navigate() behave as they do in the app, not a route of convenience.
function renderPublisherAdmin(service: ExtensionRegistryService, publisher: string) {
    return renderWithProviders(
        <Routes>
            <Route path={AdminDashboardRoutes.PUBLISHER_ADMIN} element={<PublisherAdmin />} />
            <Route path={`${AdminDashboardRoutes.PUBLISHER_ADMIN}/:publisher`} element={<PublisherAdmin />} />
        </Routes>,
        { route: `${AdminDashboardRoutes.PUBLISHER_ADMIN}/${publisher}`, mainContext: { service } }
    );
}

describe('PublisherAdmin', () => {
    // Regression: navigate() only clears the :publisher route param a render or two after
    // clearSelection() nulls `selected`. In between, the deep-link-resolution effect saw
    // publisherParam still set and !selected, and re-selected the very publisher just deleted -
    // leaving its (now stale) details on screen, which then errored refetching a gone user.
    it('drops the selection and returns to search once a forgotten user is deleted', async () => {
        const forgetUser = vi.fn().mockResolvedValue({ success: 'Forgot user deleted-user-7' });
        renderPublisherAdmin(serviceWith(forgetUser), 'octocat');

        await screen.findByText('Danger Zone');
        await userEvent.click(await screen.findByRole('button', { name: 'Forget user' }));
        await userEvent.click(screen.getByText('Forget', { selector: 'button' }));

        expect(forgetUser).toHaveBeenCalledWith('github', 'octocat');
        // Wait for the final settled state directly: the route param clearing lags the local
        // "selected" state by a render or two, so an intermediate frame (selected already null,
        // param not yet cleared) briefly renders the unrelated "no publisher found" branch.
        expect(await screen.findByText(/search for a user/i)).toBeInTheDocument();
        expect(screen.queryByText('Danger Zone')).not.toBeInTheDocument();
    });
});
