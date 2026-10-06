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
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { renderWithProviders } from '../../../support/test-providers';
import { AccessControl } from '../../../../../src/pages/admin-dashboard/access-control/access-control';
import { AdminDashboardRoutes } from '../../../../../src/pages/admin-dashboard/admin-dashboard-routes';
import { ExtensionRegistryService, AdminService } from '../../../../../src/extension-registry-service';
import { UserSearchResult } from '../../../../../src/extension-registry-types';

function serviceWith(users: UserSearchResult): ExtensionRegistryService {
    const admin = { getUsers: vi.fn().mockResolvedValue(users) } as unknown as AdminService;
    return { serverUrl: 'https://open-vsx.org', admin } as ExtensionRegistryService;
}

// AccessControl is normally mounted under this nested route by admin-dashboard.tsx; reproduce that
// here so useParams()/navigate() behave as they do in the app, not a route of convenience.
function renderAccessControl(service: ExtensionRegistryService, login: string) {
    return renderWithProviders(
        <Routes>
            <Route path={AdminDashboardRoutes.ACCESS_CONTROL} element={<AccessControl />} />
            <Route path={`${AdminDashboardRoutes.ACCESS_CONTROL}/:login`} element={<AccessControl />} />
        </Routes>,
        { route: `${AdminDashboardRoutes.ACCESS_CONTROL}/${login}`, mainContext: { service } }
    );
}

const OCTOCAT: UserSearchResult = {
    content: [
        {
            user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' },
            namespaces: []
        }
    ],
    page: { number: 0, size: 25, totalElements: 1, totalPages: 1 }
};

const TWO_USERS: UserSearchResult = {
    ...OCTOCAT,
    content: [
        ...OCTOCAT.content,
        {
            user: { loginName: 'hubot', tokensUrl: '', createTokenUrl: '', provider: 'github' },
            namespaces: []
        }
    ]
};

describe('AccessControl', () => {
    it('resolves a deep-linked user and shows their role/permission editor', async () => {
        renderAccessControl(serviceWith(OCTOCAT), 'octocat');

        expect(await screen.findByRole('button', { name: 'Admin' })).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: 'Manage extensions' })).toBeInTheDocument();
    });

    // Dropping the selection unmounts the details card and takes the draft with it, so it has to be
    // confirmed first - otherwise edits disappear with no warning at all.
    it('asks before dropping a selection that has unsaved changes', async () => {
        renderAccessControl(serviceWith(OCTOCAT), 'octocat');

        await userEvent.click(await screen.findByRole('checkbox', { name: 'Manage extensions' }));
        await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));

        expect(await screen.findByText('Discard unsaved changes?')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Keep editing' }));
        // findBy, not getBy: the dialog fades out, and until it is gone the page behind it is still
        // aria-hidden, so nothing on it is in the accessible tree yet.
        expect(await screen.findByRole('checkbox', { name: 'Manage extensions' })).toBeChecked();

        await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Discard' }));

        expect(screen.queryByRole('checkbox', { name: 'Manage extensions' })).not.toBeInTheDocument();
    });

    // MUI syncs the search box to the option just picked, which happens before the confirmation is
    // answered. Declining has to leave the box naming the user still being edited, not the one the
    // switch was refused for.
    it('keeps the search box on the edited user when a switch to another is declined', async () => {
        renderAccessControl(serviceWith(TWO_USERS), 'octocat');

        await userEvent.click(await screen.findByRole('checkbox', { name: 'Manage extensions' }));

        const search = screen.getByPlaceholderText('Search by login or display name...');
        await userEvent.click(search);
        await userEvent.click(await screen.findByText('hubot'));

        expect(await screen.findByText('Discard unsaved changes?')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Keep editing' }));

        expect(search).toHaveValue('octocat');
        expect(await screen.findByRole('checkbox', { name: 'Manage extensions' })).toBeChecked();
    });

    // A short or stale deep link substring-matches nearly every account, so without a cap this
    // walks the entire registry a page at a time, showing an empty placeholder throughout.
    it('gives up after a few pages when a deep link matches no exact login', async () => {
        const getUsers = vi.fn(async (_abort: AbortController, params?: { page?: number }) => {
            const number = params?.page ?? 0;
            return {
                content: [
                    {
                        user: {
                            loginName: `ghostwriter-${number}`,
                            tokensUrl: '',
                            createTokenUrl: '',
                            provider: 'github'
                        },
                        namespaces: []
                    }
                ],
                page: { number, size: 25, totalElements: 500, totalPages: 20 }
            };
        });
        const service = {
            serverUrl: 'https://open-vsx.org',
            admin: { getUsers } as unknown as AdminService
        } as ExtensionRegistryService;

        renderAccessControl(service, 'ghost');

        expect(await screen.findByText(/No user found/)).toBeInTheDocument();
        // The first page plus the capped walk, not all twenty.
        expect(getUsers.mock.calls.length).toBeGreaterThan(1);
        expect(getUsers.mock.calls.length).toBeLessThanOrEqual(5);
    });

    // MUI only skips onChange for the identical option object, and any refetch of the search
    // replaces those - so picking the user already on screen fires a "switch" that would discard
    // nothing, because the details card is keyed on provider/login and never remounts.
    it('does not offer to discard anything when the user already shown is picked again', async () => {
        renderAccessControl(serviceWith(OCTOCAT), 'octocat');

        await userEvent.click(await screen.findByRole('checkbox', { name: 'Manage extensions' }));

        const search = screen.getByPlaceholderText('Search by login or display name...');
        await userEvent.click(search);
        await userEvent.click(await screen.findByText('octocat'));

        expect(screen.queryByText('Discard unsaved changes?')).not.toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: 'Manage extensions' })).toBeChecked();
    });

    it('drops a selection with no unsaved changes without asking', async () => {
        renderAccessControl(serviceWith(OCTOCAT), 'octocat');

        await screen.findByRole('checkbox', { name: 'Manage extensions' });
        await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));

        expect(screen.queryByText('Discard unsaved changes?')).not.toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Manage extensions' })).not.toBeInTheDocument();
    });
});
