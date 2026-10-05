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
import { renderWithProviders } from '../../../support/test-providers';
import { AccessControlDetails } from '../../../../../src/pages/admin-dashboard/access-control/access-control-details';
import { ExtensionRegistryService, AdminService } from '../../../../../src/extension-registry-service';
import { UserData, UserRelationships } from '../../../../../src/extension-registry-types';

function entry(overrides: Partial<UserData> = {}): UserRelationships {
    return {
        user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github', ...overrides },
        namespaces: []
    };
}

function serviceWith(admin: Partial<AdminService>): ExtensionRegistryService {
    return { serverUrl: 'https://open-vsx.org', admin: admin as AdminService } as ExtensionRegistryService;
}

describe('AccessControlDetails', () => {
    it('grants a permission when its checkbox is checked', async () => {
        const updateUserPermission = vi.fn().mockResolvedValue({ success: 'Granted' });
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserPermission }) }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));

        expect(updateUserPermission).toHaveBeenCalledWith('github', 'octocat', 'manage_extensions', true);
    });

    it('revokes an already-granted permission when its checkbox is unchecked', async () => {
        const updateUserPermission = vi.fn().mockResolvedValue({ success: 'Revoked' });
        renderWithProviders(<AccessControlDetails entry={entry({ permissions: ['manage_extensions'] })} />, {
            mainContext: { service: serviceWith({ updateUserPermission }) }
        });

        const checkbox = screen.getByRole('checkbox', { name: 'Manage extensions' });
        expect(checkbox).toBeChecked();
        await userEvent.click(checkbox);

        expect(updateUserPermission).toHaveBeenCalledWith('github', 'octocat', 'manage_extensions', false);
    });

    it('reverts the checkbox when the grant fails', async () => {
        const updateUserPermission = vi.fn().mockRejectedValue(new Error('nope'));
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserPermission }) }
        });

        const checkbox = screen.getByRole('checkbox', { name: 'Manage extensions' });
        await userEvent.click(checkbox);

        expect(await screen.findByText(/nope/)).toBeInTheDocument();
        expect(checkbox).not.toBeChecked();
    });

    it('updates the role when a different one is selected', async () => {
        const updateUserRole = vi.fn().mockResolvedValue({ success: 'Updated' });
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserRole }) }
        });

        await userEvent.click(screen.getByRole('button', { name: 'Admin' }));

        expect(updateUserRole).toHaveBeenCalledWith('github', 'octocat', 'admin');
    });

    // The entry comes from the search result and is never refetched while this stays mounted, so
    // reverting to it after a later failure would show a role the server gave up two changes ago.
    it('reverts a failed role change to the one actually in effect, not the one it loaded with', async () => {
        const updateUserRole = vi
            .fn()
            .mockResolvedValueOnce({ success: 'Updated' })
            .mockRejectedValueOnce(new Error('nope'));
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserRole }) }
        });

        await userEvent.click(screen.getByRole('button', { name: 'Privileged' }));
        await userEvent.click(screen.getByRole('button', { name: 'Admin' }));

        expect(await screen.findByText(/nope/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Privileged' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'No role' })).toHaveAttribute('aria-pressed', 'false');
    });

    // Admin implies every permission server-side (UserData#hasPermission), so individual grants
    // would have no effect - the checkboxes reflect that instead of suggesting otherwise.
    it('shows every permission as checked and disabled once the role is admin', () => {
        renderWithProviders(<AccessControlDetails entry={entry({ role: 'admin' })} />, {
            mainContext: { service: serviceWith({}) }
        });

        const checkbox = screen.getByRole('checkbox', { name: 'Manage extensions' });
        expect(checkbox).toBeChecked();
        expect(checkbox).toBeDisabled();
    });
});
