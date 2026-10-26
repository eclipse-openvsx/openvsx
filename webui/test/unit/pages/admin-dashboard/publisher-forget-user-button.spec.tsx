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
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { PublisherForgetUserButton } from '../../../../src/pages/admin-dashboard/publisher-forget-user-button';
import { UpdateContext } from '../../../../src/pages/admin-dashboard/publisher-admin';
import { ExtensionRegistryService, AdminService } from '../../../../src/extension-registry-service';
import { PublisherInfo } from '../../../../src/extension-registry-types';

function publisherInfo(): PublisherInfo {
    return {
        user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' },
        extensions: [],
        activeAccessTokenNum: 0
    };
}

function serviceWith(forgetUser: AdminService['forgetUser']): ExtensionRegistryService {
    return {
        serverUrl: 'https://open-vsx.org',
        admin: { forgetUser } as unknown as AdminService
    } as ExtensionRegistryService;
}

const openButton = () => screen.getByRole('button', { name: 'Forget user' });
// MUI's Dialog/Modal transition trips a jsdom getComputedStyle bug that isInaccessible's
// ancestor walk hits for any getByRole query while the dialog is open (reproduced with a
// bare MUI Dialog, unrelated to this component) - query dialog content by text instead.
const cancelButton = () => screen.getByText('Cancel', { selector: 'button' });
const confirmButton = () => screen.getByText('Forget', { selector: 'button' });

describe('PublisherForgetUserButton', () => {
    it('opens a confirmation dialog naming the publisher before calling the endpoint', async () => {
        const forgetUser = vi.fn().mockResolvedValue({ success: 'ok' });
        renderWithProviders(<PublisherForgetUserButton publisherInfo={publisherInfo()} />, {
            mainContext: { service: serviceWith(forgetUser) }
        });

        await userEvent.click(openButton());

        expect(screen.getByText(/octocat/)).toBeInTheDocument();
        expect(forgetUser).not.toHaveBeenCalled();
    });

    it('cancelling the dialog does not call the endpoint', async () => {
        const forgetUser = vi.fn().mockResolvedValue({ success: 'ok' });
        renderWithProviders(<PublisherForgetUserButton publisherInfo={publisherInfo()} />, {
            mainContext: { service: serviceWith(forgetUser) }
        });

        await userEvent.click(openButton());
        await userEvent.click(cancelButton());

        expect(forgetUser).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.queryByText(/octocat/)).not.toBeInTheDocument());
    });

    it('calls forgetUser with the publisher and notifies the surrounding page on success', async () => {
        const forgetUser = vi.fn().mockResolvedValue({ success: 'ok' });
        const handleUserDeleted = vi.fn();
        renderWithProviders(
            <UpdateContext.Provider value={{ handleUpdate: () => {}, handleUserDeleted }}>
                <PublisherForgetUserButton publisherInfo={publisherInfo()} />
            </UpdateContext.Provider>,
            { mainContext: { service: serviceWith(forgetUser) } }
        );

        await userEvent.click(openButton());
        await userEvent.click(confirmButton());

        expect(forgetUser).toHaveBeenCalledWith('github', 'octocat');
        // The dialog closes once the request has resolved.
        await waitFor(() => expect(screen.queryByText(/octocat/)).not.toBeInTheDocument());
        expect(handleUserDeleted).toHaveBeenCalled();
    });

    it('reports a rejected request to the page and leaves the dialog open', async () => {
        // The service rejects on failure, including a 200 carrying an error body.
        const forgetUser = vi.fn().mockRejectedValue({ error: 'boom' });
        const handleError = vi.fn();
        const handleUserDeleted = vi.fn();
        renderWithProviders(
            <UpdateContext.Provider value={{ handleUpdate: () => {}, handleUserDeleted }}>
                <PublisherForgetUserButton publisherInfo={publisherInfo()} />
            </UpdateContext.Provider>,
            { mainContext: { service: serviceWith(forgetUser), handleError } }
        );

        await userEvent.click(openButton());
        await userEvent.click(confirmButton());

        await waitFor(() => expect(handleError).toHaveBeenCalled());
        expect(handleUserDeleted).not.toHaveBeenCalled();
        expect(screen.getByText(/octocat/)).toBeInTheDocument();
    });
});
