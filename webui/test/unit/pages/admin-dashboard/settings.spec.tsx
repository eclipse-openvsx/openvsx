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
import { useSiteSettings } from '../../../../src/components/use-site-settings';
import { renderWithProviders } from '../../support/test-providers';

const defaults: Settings = {
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    maxOverrideSize: Number.MAX_SAFE_INTEGER,
    bannerEnabled: false,
    bannerMessage: '',
    bannerSeverity: 'info',
    bannerDismissId: ''
};

const configured: Settings = {
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    maxOverrideSize: Number.MAX_SAFE_INTEGER,
    bannerEnabled: true,
    bannerMessage: 'Heads up',
    bannerSeverity: 'info',
    bannerDismissId: 'token-1'
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (settings: Settings = defaults, getSiteSettings = vi.fn().mockResolvedValue({})) => {
    const admin = {
        getSettings: vi.fn().mockResolvedValue(settings),
        updateSettings: vi.fn().mockImplementation((updated: Settings) => Promise.resolve(updated))
    };
    renderWithProviders(<RuntimeSettingsPage />, {
        mainContext: { service: { admin, getSiteSettings } as unknown as ExtensionRegistryService }
    });
    return admin;
};

const sized = (overrides: Partial<Settings>): Settings => ({ ...defaults, ...overrides });

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
            expect(admin.updateSettings).toHaveBeenCalledWith({ bannerMessage: 'Maintenance tonight' })
        );
    });

    it('saves the banner severity alongside the message', async () => {
        const admin = mountPage(configured);

        await userEvent.click(await screen.findByText('Warning'));
        await save();

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith({
                bannerSeverity: 'warning',
                bannerDismissId: expect.any(String)
            })
        );
    });

    it('turns the banner on and off without touching the message', async () => {
        const admin = mountPage(configured);

        await userEvent.click(await screen.findByLabelText('Toggle banner'));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ bannerEnabled: false }));
    });

    it('previews the message as the banner will render it', async () => {
        mountPage({ ...configured, bannerMessage: 'Heads **up**' });

        const emphasised = await screen.findByText('up');
        expect(emphasised.tagName).toBe('STRONG');
    });

    it('renders a stored severity the server no longer accepts as info', async () => {
        mountPage({ ...configured, bannerSeverity: 'critical' as Settings['bannerSeverity'] });

        expect(await screen.findByText('Heads up', { selector: 'p' })).toBeVisible();
    });

    // /api/-/settings can be answered from a browser or CDN cache, so a refetch could return the
    // pre-save banner; the page seeds it from the update response instead.
    it('shows the saved banner in the public settings without refetching them', async () => {
        const getSiteSettings = vi.fn().mockResolvedValue({});
        const saved: Settings = { ...configured, bannerMessage: 'Fresh' };
        const admin = {
            getSettings: vi.fn().mockResolvedValue(configured),
            updateSettings: vi.fn().mockResolvedValue(saved)
        };
        const Probe = () => <output>{useSiteSettings().data?.bannerMessage ?? 'none'}</output>;
        renderWithProviders(
            <>
                <RuntimeSettingsPage />
                <Probe />
            </>,
            { mainContext: { service: { admin, getSiteSettings } as unknown as ExtensionRegistryService } }
        );
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('none'));

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        await save();

        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Fresh'));
        expect(getSiteSettings).toHaveBeenCalledTimes(1);
    });

    it('serves nothing public once the saved banner is off', async () => {
        const saved: Settings = { ...configured, bannerEnabled: false };
        const admin = {
            getSettings: vi.fn().mockResolvedValue(configured),
            updateSettings: vi.fn().mockResolvedValue(saved)
        };
        const getSiteSettings = vi.fn().mockResolvedValue({ bannerMessage: 'Heads up' });
        const Probe = () => <output>{useSiteSettings().data?.bannerMessage ?? 'none'}</output>;
        renderWithProviders(
            <>
                <RuntimeSettingsPage />
                <Probe />
            </>,
            { mainContext: { service: { admin, getSiteSettings } as unknown as ExtensionRegistryService } }
        );
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Heads up'));

        await userEvent.click(await screen.findByLabelText('Toggle banner'));
        await save();

        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('none'));
    });

    it('rotates the dismiss token by default once the banner is edited', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        expect(screen.getByLabelText(SHOW_AGAIN)).toBeChecked();
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalled());
        const saved = admin.updateSettings.mock.calls[0][0] as Settings;
        expect(saved.bannerMessage).toBe('Heads up!');
        expect(saved.bannerDismissId).not.toBe('token-1');
        expect(saved.bannerDismissId).toHaveLength(36);
    });

    it('keeps the dismissals when the admin unchecks the offer', async () => {
        const admin = mountPage(configured);

        await userEvent.type(await screen.findByLabelText('Message'), '!');
        await userEvent.click(screen.getByLabelText(SHOW_AGAIN));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ bannerMessage: 'Heads up!' }));
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
                bannerMessage: 'Maintenance tonight',
                bannerEnabled: true
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

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ bannerMessage: '' }));
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

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ readOnly: true }));
    });

    it('keeps Save disabled until a setting actually changes', async () => {
        mountPage(configured);

        expect(await screen.findByLabelText('Message')).toHaveValue('Heads up');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        await userEvent.type(screen.getByLabelText('Message'), '!');
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('shows the current default max extension size in MB', async () => {
        mountPage(sized({ maxExtensionSize: 512 * 1024 * 1024 }));

        await waitFor(() => expect(screen.getByLabelText('Max extension size (MB)')).toHaveValue(512));
    });

    it('disables save once the typed size is not greater than zero', async () => {
        const user = userEvent.setup();
        mountPage(defaults);

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '0');

        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    });

    /**
     * The server refuses a default above ovsx.publishing.max-override-size, so saying so here beats
     * letting the admin discover it from the save's error response.
     */
    it('disables save once the typed size exceeds the override ceiling', async () => {
        const user = userEvent.setup();
        mountPage(sized({ maxOverrideSize: 1024 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '2048');

        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
        expect(screen.getByText(/at most 1073741824 bytes/i)).toBeInTheDocument();
    });

    /**
     * Flooring a ceiling that is not a whole number of MB understated it - a 1.5 MiB ceiling read "at
     * most 1 MB" even though up to 1.5 MB was actually valid.
     */
    it('does not floor a ceiling that is not a whole number of MB', async () => {
        const user = userEvent.setup();
        mountPage(sized({ maxOverrideSize: 1.5 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '2');

        expect(screen.getByText(/at most 1572864 bytes \(~1\.50 MB\)/i)).toBeInTheDocument();
    });

    /**
     * Rounding the MB approximation can overstate the ceiling near a boundary - 2 MiB - 1 byte rounds
     * to "2.00 MB", which would name a value (2 MB) the server actually rejects. The exact byte count
     * must be the authoritative part of the message.
     */
    it('names the exact byte ceiling near a rounding boundary', async () => {
        const user = userEvent.setup();
        mountPage(sized({ maxOverrideSize: 2 * 1024 * 1024 - 1 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '3');

        expect(screen.getByText(/at most 2097151 bytes/i)).toBeInTheDocument();
    });

    // parseInt stopped at the decimal point, so this used to save 1 MB while the field still read
    // 1.5 - a limit nobody chose, applied silently.
    it('keeps a fractional MB value instead of truncating it', async () => {
        const user = userEvent.setup();
        const admin = mountPage(sized({ maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '1.5');

        await user.click(screen.getByRole('button', { name: /save/i }));
        await user.click(await screen.findByRole('button', { name: 'Apply' }));

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith(
                expect.objectContaining({ maxExtensionSize: 1.5 * 1024 * 1024 })
            )
        );
    });

    /**
     * Sending the whole object would carry every other setting as this page last read it, reverting
     * anything another admin changed meanwhile. Only what this admin touched goes to the server.
     */
    it('sends only the settings that were changed', async () => {
        const user = userEvent.setup();
        const admin = mountPage(sized({ readOnly: true, maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '1024');

        await user.click(screen.getByRole('button', { name: /save/i }));
        await user.click(await screen.findByRole('button', { name: 'Apply' }));

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalled());
        expect(Object.keys(admin.updateSettings.mock.calls[0][0])).toEqual(['maxExtensionSize']);
    });

    /**
     * The server stores the limit as a long, so one past JavaScript's safe-integer range arrives here
     * intact. Validating a field the save will not even send would hold every other setting hostage
     * until the admin replaced a size they never meant to touch.
     */
    it('saves an unrelated setting while the stored size exceeds the safe-integer range', async () => {
        const user = userEvent.setup();
        const admin = mountPage(sized({ readOnly: false, maxExtensionSize: Number.MAX_SAFE_INTEGER + 1 }));

        const toggle = await screen.findByLabelText('Toggle Read-only mode');
        await waitFor(() => expect(toggle).toBeEnabled());
        await user.click(toggle);

        await user.click(screen.getByRole('button', { name: /save/i }));
        await user.click(await screen.findByRole('button', { name: 'Apply' }));

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalled());
        expect(Object.keys(admin.updateSettings.mock.calls[0][0])).toEqual(['readOnly']);
    });

    /**
     * Number('') is 0, so deriving the field's value from the draft rewrote an emptied field as "0"
     * on the keystroke that cleared it - the admin could not retype the number without selecting all
     * of it first.
     */
    it('lets the size field be cleared instead of filling it with a zero', async () => {
        const user = userEvent.setup();
        mountPage(sized({ maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);

        expect(input).toHaveValue(null);
        // empty is not a size, so there is nothing to save
        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    });

    it('converts the typed MB value to bytes and saves it', async () => {
        const user = userEvent.setup();
        const admin = mountPage(sized({ maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '1024');

        await user.click(screen.getByRole('button', { name: /save/i }));
        await user.click(await screen.findByRole('button', { name: 'Apply' }));

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith(
                expect.objectContaining({ maxExtensionSize: 1024 * 1024 * 1024 })
            )
        );
    });
});
