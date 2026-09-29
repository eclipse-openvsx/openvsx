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

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-severity': 'warning' }));
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

    it('rotates the dismiss token only when asked to show the banner again', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        await userEvent.click(screen.getByText('Show again to everyone'));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalled());
        const saved = admin.updateSettings.mock.calls[0][0] as Settings;
        expect(saved['banner-message']).toBe('Heads up!');
        expect(saved['banner-dismiss-id']).not.toBe('token-1');
        expect(saved['banner-dismiss-id']).toHaveLength(36);
    });

    it('sends only the settings it changed, so a concurrent edit is not reverted', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ 'banner-message': 'Heads up!' }));
    });

    it('keeps Save disabled until a setting actually changes', async () => {
        mountPage(configured);

        expect(await screen.findByLabelText('Message')).toHaveValue('Heads up');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        await userEvent.type(screen.getByLabelText('Message'), '!');
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });
});
