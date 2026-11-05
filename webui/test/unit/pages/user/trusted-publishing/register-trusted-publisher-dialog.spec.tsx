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

import { describe, expect, it, vi, Mock } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../../support/test-providers';
import {
    RegisterTrustedPublisherDialog,
    RegisterTrustedPublisherDialogProps
} from '../../../../../src/pages/user/trusted-publishing/register-trusted-publisher-dialog';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { PageSettings } from '../../../../../src/page-settings';
import { gitHubProvider, testUser, trustedPublisher } from '../../../support/trusted-publishing';

const URL_FOO = '/user/namespace/foo/trusted-publishing';
// the dialog description reads pageSettings.urls (docs link), which the harness default lacks
const pageSettings = { urls: {} } as PageSettings;

// Namespace, provider and extension are preset so tests exercise the registration fields.
function renderDialog(
    props: Partial<RegisterTrustedPublisherDialogProps> = {},
    register: Mock = vi.fn().mockResolvedValue(trustedPublisher())
) {
    const onClose = vi.fn();
    const onRegistered = vi.fn();
    const service = { registerTrustedPublisher: register } as unknown as ExtensionRegistryService;
    renderWithProviders(
        <RegisterTrustedPublisherDialog
            open
            onClose={onClose}
            onRegistered={onRegistered}
            namespaces={[
                {
                    name: 'foo',
                    registrableExtensions: ['bar'],
                    registeredExtensions: [],
                    trustedPublishingUrl: URL_FOO
                }
            ]}
            namespace='foo'
            lockedExtension='bar'
            initialProvider='github'
            providers={[gitHubProvider]}
            {...props}
        />,
        { mainContext: { service, user: testUser, pageSettings } }
    );
    return { onClose, onRegistered, register };
}

const fillRequiredFields = async (ue: ReturnType<typeof userEvent.setup>, owner = 'octo') => {
    await ue.type(screen.getByRole('textbox', { name: /Repository owner/ }), owner);
    await ue.type(screen.getByRole('textbox', { name: /Repository name/ }), 'vsx');
    await ue.type(screen.getByRole('textbox', { name: /Workflow file/ }), 'release.yml');
};

describe('RegisterTrustedPublisherDialog', () => {
    it('enables Register only once all required fields are filled', async () => {
        const ue = userEvent.setup();
        renderDialog();
        const registerButton = screen.getByRole('button', { name: 'Register' });

        expect(registerButton).toBeDisabled();
        await ue.type(screen.getByRole('textbox', { name: /Repository owner/ }), 'octo');
        await ue.type(screen.getByRole('textbox', { name: /Repository name/ }), 'vsx');
        expect(registerButton).toBeDisabled();

        // the optional environment field stays empty
        await ue.type(screen.getByRole('textbox', { name: /Workflow file/ }), 'release.yml');
        expect(registerButton).toBeEnabled();
    });

    it('submits the trimmed registration without empty optional fields, then closes', async () => {
        const ue = userEvent.setup();
        const { onClose, onRegistered, register } = renderDialog();
        await fillRequiredFields(ue, ' octo ');

        await ue.click(screen.getByRole('button', { name: 'Register' }));

        await waitFor(() =>
            expect(register).toHaveBeenCalledWith(URL_FOO, {
                provider: 'github',
                namespace: 'foo',
                extension: 'bar',
                registration: { owner: 'octo', repo: 'vsx', workflow: 'release.yml' }
            })
        );
        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(onRegistered).toHaveBeenCalled();
    });

    it('shows the registration code once and keeps the dialog open for a pending registration', async () => {
        const ue = userEvent.setup();
        const pending = trustedPublisher({
            pending: true,
            registrationCode: 'one-time-code',
            registrationCodeExpiry: new Date(Date.now() + 7 * 86_400_000).toISOString()
        });
        const { onClose, onRegistered } = renderDialog({}, vi.fn().mockResolvedValue(pending));
        await fillRequiredFields(ue);

        await ue.click(screen.getByRole('button', { name: 'Register' }));

        expect(await screen.findByLabelText('Registration code')).toHaveValue('one-time-code');
        expect(screen.getByText(/will not be shown again/)).toBeInTheDocument();
        expect(screen.getByText('in 7 days')).toBeInTheDocument();
        expect(onRegistered).toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();

        await ue.click(screen.getByRole('button', { name: 'Done' }));
        expect(onClose).toHaveBeenCalled();
    });

    it('keeps the code on screen when escape is pressed or the form is submitted again', async () => {
        const ue = userEvent.setup();
        const pending = trustedPublisher({
            pending: true,
            registrationCode: 'one-time-code',
            registrationCodeExpiry: new Date(Date.now() + 7 * 86_400_000).toISOString()
        });
        const register = vi.fn().mockResolvedValue(pending);
        const { onClose } = renderDialog({}, register);
        await fillRequiredFields(ue);
        await ue.click(screen.getByRole('button', { name: 'Register' }));
        const field = await screen.findByLabelText('Registration code');

        await ue.keyboard('{Escape}');
        await ue.type(field, '{Enter}');

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByLabelText('Registration code')).toHaveValue('one-time-code');
        expect(register).toHaveBeenCalledTimes(1);
    });

    it('says so when the code cannot be copied', async () => {
        const ue = userEvent.setup();
        const pending = trustedPublisher({ pending: true, registrationCode: 'one-time-code' });
        renderDialog({}, vi.fn().mockResolvedValue(pending));
        await fillRequiredFields(ue);
        await ue.click(screen.getByRole('button', { name: 'Register' }));
        await screen.findByLabelText('Registration code');
        vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'));

        await ue.click(screen.getByRole('button', { name: 'Copy registration code' }));

        expect(await screen.findByText(/could not be copied/)).toBeInTheDocument();
    });

    it('shows a server error inline and keeps the dialog open', async () => {
        // handleError() also logs the raw error to the console; expected here since we're
        // intentionally triggering it, so silence it rather than let it dirty test output.
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        const ue = userEvent.setup();
        const register = vi.fn().mockImplementation(() => Promise.reject({ error: 'Repository not found' }));
        const { onClose } = renderDialog({}, register);
        await fillRequiredFields(ue);

        await ue.click(screen.getByRole('button', { name: 'Register' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Repository not found');
        expect(onClose).not.toHaveBeenCalled();
        consoleError.mockRestore();
    });

    it('explains when the namespace has no extensions to register for', () => {
        renderDialog({
            lockedExtension: undefined,
            namespaces: [
                { name: 'foo', registrableExtensions: [], registeredExtensions: [], trustedPublishingUrl: URL_FOO }
            ]
        });

        expect(screen.getByText(/Publish an extension before registering/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Register' })).toBeDisabled();
    });

    // an extension takes at most one trusted publisher, so a fully registered namespace offers nothing
    it('explains when every extension already has a trusted publisher', () => {
        renderDialog({
            lockedExtension: undefined,
            namespaces: [
                {
                    name: 'foo',
                    registrableExtensions: [],
                    registeredExtensions: ['bar'],
                    trustedPublishingUrl: URL_FOO
                }
            ]
        });

        expect(screen.getByText(/Every active extension in this namespace already has/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Register' })).toBeDisabled();
    });

    it('offers only the registrable extensions', async () => {
        const ue = userEvent.setup();
        renderDialog({
            lockedExtension: undefined,
            namespaces: [
                {
                    name: 'foo',
                    registrableExtensions: ['free'],
                    registeredExtensions: ['taken'],
                    trustedPublishingUrl: URL_FOO
                }
            ]
        });

        await ue.click(screen.getByRole('combobox', { name: /Extension/ }));
        expect(screen.getByRole('option', { name: 'free' })).toBeInTheDocument();
        expect(screen.queryByRole('option', { name: 'taken' })).not.toBeInTheDocument();
    });
});
