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
 ********************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RuntimeSettingsPage } from '../../../../src/pages/admin-dashboard/settings';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Settings } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const defaults: Settings = {
    'read-only': false,
    'banner-enabled': false,
    'banner-message': '',
    'banner-severity': 'info',
    'banner-dismiss-id': ''
};

const configured: Settings = {
    'read-only': false,
    'banner-enabled': true,
    'banner-message': 'Heads up',
    'banner-severity': 'info',
    'banner-dismiss-id': 'token-1'
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (settings: Settings = defaults) => {
    const admin = {
        getSettings: vi.fn().mockResolvedValue(settings),
        updateSettings: vi.fn().mockImplementation((updated: Settings) => Promise.resolve(updated))
    };
    renderWithProviders(<RuntimeSettingsPage />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

const save = async () => {
    await userEvent.click(screen.getByText('Save'));
    await userEvent.click(screen.getByText('Apply'));
};

const SHOW_AGAIN = 'Show again to everyone who dismissed it';

describe('RuntimeSettingsPage', () => {
    it('saves a banner message typed by the admin', async () => {
        const admin = mountPage();

        await userEvent.type(await screen.findByLabelText('Message'), 'Maintenance tonight');
        await save();

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-message': 'Maintenance tonight' })
        );
    });

    it('saves the banner severity alongside the message', async () => {
        const admin = mountPage(configured);

        await userEvent.click(await screen.findByText('Warning'));
        await save();

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith({
                'banner-severity': 'warning',
                'banner-dismiss-id': expect.any(String)
            })
        );
    });

    it('turns the banner on and off without touching the message', async () => {
        const admin = mountPage(configured);

        await userEvent.click(await screen.findByLabelText('Toggle banner'));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-enabled': false }));
    });

    it('previews the message as the banner will render it', async () => {
        mountPage({ ...configured, 'banner-message': 'Heads **up**' });

        const emphasised = await screen.findByText('up');
        expect(emphasised.tagName).toBe('STRONG');
    });

    it('rotates the dismiss token by default once the banner is edited', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        expect(screen.getByLabelText(SHOW_AGAIN)).toBeChecked();
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalled());
        const saved = admin.updateSettings.mock.calls[0][0] as Settings;
        expect(saved['banner-message']).toBe('Heads up!');
        expect(saved['banner-dismiss-id']).not.toBe('token-1');
        expect(saved['banner-dismiss-id']).toHaveLength(36);
    });

    it('keeps the dismissals when the admin unchecks the offer', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        await userEvent.click(screen.getByLabelText(SHOW_AGAIN));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-message': 'Heads up!' }));
    });

    it('checks the offer again once a reverted edit is redone', async () => {
        mountPage(configured);

        const message = await screen.findByLabelText('Message');
        await userEvent.type(message, '!');
        await userEvent.click(screen.getByLabelText(SHOW_AGAIN));
        await userEvent.type(message, '{backspace}');

        expect(screen.queryByLabelText(SHOW_AGAIN)).toBeNull();
        await userEvent.type(message, '?');
        expect(screen.getByLabelText(SHOW_AGAIN)).toBeChecked();
    });

    it('does not offer to show a first banner again, since nobody has dismissed it', async () => {
        const admin = mountPage();

        await userEvent.type(await screen.findByLabelText('Message'), 'Maintenance tonight');
        await userEvent.click(screen.getByLabelText('Toggle banner'));

        expect(screen.queryByLabelText(SHOW_AGAIN)).toBeNull();
        await save();

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith({
                'banner-message': 'Maintenance tonight',
                'banner-enabled': true
            })
        );
    });

    it('does not offer to show a banner again while it is being switched off', async () => {
        mountPage(configured);

        await userEvent.click(await screen.findByLabelText('Toggle banner'));

        expect(screen.queryByLabelText(SHOW_AGAIN)).toBeNull();
        expect(screen.getByText('nobody will see a banner.')).toBeInTheDocument();
    });

    it('does not offer to show a banner again while its message is empty', async () => {
        const admin = mountPage(configured);

        await userEvent.clear(await screen.findByLabelText('Message'));

        expect(screen.queryByLabelText(SHOW_AGAIN)).toBeNull();
        expect(screen.getByText('nothing will be shown.')).toBeInTheDocument();
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-message': '' }));
    });

    it('says who the save reaches, and follows the offer', async () => {
        mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        expect(
            screen.getByText('everyone will see this banner, including the people who dismissed it.')
        ).toBeInTheDocument();

        await userEvent.click(screen.getByLabelText(SHOW_AGAIN));
        expect(screen.getByText('people who already dismissed this banner will not see it again.')).toBeInTheDocument();
    });

    it('says whether the preview is the live banner or the pending one', async () => {
        mountPage(configured);

        expect(await screen.findByText('Preview - what visitors see right now')).toBeInTheDocument();

        await userEvent.type(screen.getByLabelText('Message'), '!');
        expect(screen.getByText('Preview - what visitors will see after you save')).toBeInTheDocument();
    });

    it('leaves the banner alone when only a flag is saved', async () => {
        const admin = mountPage(configured);

        await userEvent.click(await screen.findByLabelText('Toggle Read-only mode'));

        expect(screen.getByText('Preview - what visitors see right now')).toBeInTheDocument();
        expect(screen.queryByText('On save:')).toBeNull();
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'read-only': true }));
    });

    it('keeps Save disabled until a setting actually changes', async () => {
        mountPage(configured);

        expect(await screen.findByLabelText('Message')).toHaveValue('Heads up');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        await userEvent.type(screen.getByLabelText('Message'), '!');
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });
});
