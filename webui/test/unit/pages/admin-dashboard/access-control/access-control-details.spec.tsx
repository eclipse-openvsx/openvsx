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
    // The whole point of the Save button: nothing reaches the server until it is pressed, and what
    // it then sends is the state the admin composed - role and permissions in one request.
    it('sends the role and permissions together, and only once Save is clicked', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        renderWithProviders(<AccessControlDetails entry={entry({ permissions: ['manage_caches'] })} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('button', { name: 'Privileged' }));
        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));
        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage caches' }));
        expect(updateUserAccess).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(updateUserAccess).toHaveBeenCalledTimes(1);
        expect(updateUserAccess).toHaveBeenCalledWith('github', 'octocat', {
            role: 'privileged',
            permissions: ['manage_extensions']
        });
    });

    it('has nothing to save until something is changed', async () => {
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({}) }
        });

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Reset' })).toBeDisabled();

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));

        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('restores the loaded access when Reset is clicked', async () => {
        const updateUserAccess = vi.fn();
        renderWithProviders(<AccessControlDetails entry={entry({ permissions: ['manage_caches'] })} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('button', { name: 'Admin' }));
        await userEvent.click(screen.getByRole('button', { name: 'Reset' }));

        expect(screen.getByRole('button', { name: 'No role' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('checkbox', { name: 'Manage caches' })).toBeChecked();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        expect(updateUserAccess).not.toHaveBeenCalled();
    });

    // Keeping the draft is what makes the failure retryable - clearing it would make the admin
    // reconstruct a change the server never took.
    it('keeps the draft and reports the error when the save fails', async () => {
        const updateUserAccess = vi.fn().mockRejectedValue(new Error('nope'));
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText(/nope/)).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: 'Manage extensions' })).toBeChecked();
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('reports the draft as dirty only while it differs from what was saved', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        const onDirtyChange = vi.fn();
        renderWithProviders(<AccessControlDetails entry={entry()} onDirtyChange={onDirtyChange} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));
        expect(onDirtyChange).toHaveBeenLastCalledWith(true);

        await userEvent.click(screen.getByRole('button', { name: 'Save' }));
        expect(await screen.findByRole('button', { name: 'Saved' })).toBeInTheDocument();
        expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    });

    // MainContext.user is read once at startup. Without this the admin who just demoted themselves
    // keeps a menu and dashboard they no longer qualify for until they happen to reload.
    it('refreshes the logged-in user after saving their own access', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        const updateUser = vi.fn();
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: {
                service: serviceWith({ updateUserAccess }),
                user: { loginName: 'octocat', provider: 'github' } as UserData,
                updateUser
            }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateUser).toHaveBeenCalled());
    });

    it('leaves the logged-in user alone when saving somebody else', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        const updateUser = vi.fn();
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: {
                service: serviceWith({ updateUserAccess }),
                user: { loginName: 'root', provider: 'github' } as UserData,
                updateUser
            }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage extensions' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('button', { name: 'Saved' })).toBeInTheDocument();
        expect(updateUser).not.toHaveBeenCalled();
    });

    // Storing grants alongside the admin role means nothing while it is held, and resurfaces them
    // as standalone grants the next time someone demotes the user - which nobody chose.
    it('sends no individual permissions when the role being saved is admin', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage scans' }));
        await userEvent.click(screen.getByRole('button', { name: 'Admin' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(updateUserAccess).toHaveBeenCalledWith('github', 'octocat', { role: 'admin', permissions: [] });
    });

    // The draft is kept rather than wiped, so changing your mind about the role does not silently
    // cost you the ticks you made before changing it.
    it('restores the drafted permissions when the role moves back off admin', async () => {
        const updateUserAccess = vi.fn().mockResolvedValue({ success: 'Updated' });
        renderWithProviders(<AccessControlDetails entry={entry()} />, {
            mainContext: { service: serviceWith({ updateUserAccess }) }
        });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Manage scans' }));
        await userEvent.click(screen.getByRole('button', { name: 'Admin' }));
        await userEvent.click(screen.getByRole('button', { name: 'Privileged' }));

        expect(screen.getByRole('checkbox', { name: 'Manage scans' })).toBeChecked();

        await userEvent.click(screen.getByRole('button', { name: 'Save' }));
        expect(updateUserAccess).toHaveBeenCalledWith('github', 'octocat', {
            role: 'privileged',
            permissions: ['manage_scans']
        });
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
