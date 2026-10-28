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
import { PageSettings } from '../../../../src/page-settings';
import { AdminSettingsSection } from '../../../../src/pages/admin-dashboard/settings-sections';
import { useSiteSettings } from '../../../../src/components/use-site-settings';
import { renderWithProviders } from '../../support/test-providers';

const defaults: Settings = {
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    maxOverrideSize: Number.MAX_SAFE_INTEGER,
    downloadAnalyticsVisible: false,
    bannerEnabled: false,
    bannerMessage: '',
    bannerSeverity: 'info',
    bannerDismissId: ''
};

const configured: Settings = {
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    maxOverrideSize: Number.MAX_SAFE_INTEGER,
    downloadAnalyticsVisible: false,
    bannerEnabled: true,
    bannerMessage: 'Heads up',
    bannerSeverity: 'info',
    bannerDismissId: 'token-1'
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (settings: Settings = defaults, adminSettingsSections?: AdminSettingsSection[]) => {
    const admin = {
        getSettings: vi.fn().mockResolvedValue(settings),
        updateSettings: vi.fn().mockImplementation((updated: Settings) => Promise.resolve(updated))
    };
    const getSiteSettings = vi.fn().mockResolvedValue({});
    const pageSettings = {
        elements: { adminSettingsSections },
        urls: { extensionDefaultIcon: '/default-icon.png', namespaceAccessInfo: '/namespace-access' }
    } as PageSettings;
    renderWithProviders(<RuntimeSettingsPage />, {
        mainContext: { service: { admin, getSiteSettings } as unknown as ExtensionRegistryService, pageSettings }
    });
    return admin;
};

const sized = (overrides: Partial<Settings>): Settings => ({ ...defaults, ...overrides });

const save = async () => {
    await userEvent.click(screen.getByText('Save'));
    await userEvent.click(screen.getByText('Apply'));
};

const SHOW_AGAIN = 'Show again to everyone who dismissed it';
const ANALYTICS_TOGGLE = 'Toggle Weekly downloads on the extension page';

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

    it('saves the download analytics switch on its own', async () => {
        const admin = mountPage();

        await userEvent.click(await screen.findByLabelText(ANALYTICS_TOGGLE));
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ downloadAnalyticsVisible: true }));
    });

    // The server refuses the switch where it has no analytics configured, so the page does not offer it.
    it('leaves out the Analytics section where the server has no download analytics configured', async () => {
        const { downloadAnalyticsVisible: _, ...unconfigured } = defaults;
        mountPage(unconfigured);

        await screen.findByLabelText('Toggle Read-only mode');
        expect(screen.queryByRole('heading', { name: 'Analytics' })).toBeNull();
        expect(screen.queryByLabelText(ANALYTICS_TOGGLE)).toBeNull();
    });

    // /api/-/settings is the public copy of the switch, and the same cache that seeds the banner
    // seeds it, so a tab left open does not keep serving the pre-save value.
    it('seeds the public settings with the saved download analytics switch', async () => {
        const saved: Settings = { ...defaults, downloadAnalyticsVisible: true };
        const admin = {
            getSettings: vi.fn().mockResolvedValue(defaults),
            updateSettings: vi.fn().mockResolvedValue(saved)
        };
        const getSiteSettings = vi.fn().mockResolvedValue({});
        const Probe = () => <output>{String(useSiteSettings().data?.downloadAnalyticsVisible ?? 'unknown')}</output>;
        renderWithProviders(
            <>
                <RuntimeSettingsPage />
                <Probe />
            </>,
            { mainContext: { service: { admin, getSiteSettings } as unknown as ExtensionRegistryService } }
        );
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('unknown'));

        await userEvent.click(await screen.findByLabelText(ANALYTICS_TOGGLE));
        await save();

        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('true'));
    });

    it('sorts a contributed section among the built-in ones by title and saves its switch', async () => {
        const withAudit = { ...defaults, auditLogEnabled: false };
        const admin = mountPage(withAudit, [
            {
                id: 'audit',
                title: 'Audit',
                description: 'Who did what.',
                flags: [{ key: 'auditLogEnabled', title: 'Audit log', description: 'Records every admin action.' }]
            }
        ]);

        // The Analytics section only appears once the settings are loaded, so wait for them first.
        const auditToggle = await screen.findByLabelText('Toggle Audit log');
        const headings = screen.getAllByRole('heading', { level: 2 }).map(heading => heading.textContent);
        expect(headings).toEqual(['Analytics', 'Audit', 'Publishing', 'Registry', 'Site settings']);
        await userEvent.click(auditToggle);
        await save();

        await waitFor(() => expect(admin.updateSettings).toHaveBeenCalledWith({ auditLogEnabled: true }));
    });

    it('appends a contributed switch to the built-in section it names', async () => {
        const withRetention = { ...defaults, retentionEnabled: true };
        mountPage(withRetention, [
            {
                id: 'analytics',
                flags: [{ key: 'retentionEnabled', title: 'Retention', description: 'Keeps the raw events.' }]
            }
        ]);

        expect(await screen.findByLabelText('Toggle Retention')).toBeChecked();
        expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(4);
        const toggles = screen.getAllByLabelText(/^Toggle /).map(toggle => toggle.getAttribute('aria-label'));
        expect(toggles).toEqual([ANALYTICS_TOGGLE, 'Toggle Retention', 'Toggle Read-only mode', 'Toggle banner']);
    });

    // Contributed switches can only be added: one that names a built-in key would otherwise render a
    // second control over the same setting.
    it('ignores a contributed switch over a setting the page already shows', async () => {
        mountPage(defaults, [
            {
                id: 'registry',
                flags: [{ key: 'readOnly', title: 'Freeze', description: 'The same switch under another name.' }]
            }
        ]);

        await screen.findByLabelText('Toggle Read-only mode');
        expect(screen.queryByLabelText('Toggle Freeze')).toBeNull();
    });

    // The banner and the size limit are not switches, but they are settings the page renders, and a
    // key repeated within one contribution would render two controls over one setting.
    it('ignores a contributed switch over a field the page renders itself, and a key it has already accepted', async () => {
        mountPage({ ...defaults, retentionEnabled: true }, [
            {
                id: 'audit',
                flags: [
                    { key: 'bannerEnabled', title: 'Banner again', description: 'A second control over the banner.' },
                    { key: 'retentionEnabled', title: 'Retention', description: 'Keeps the raw events.' },
                    { key: 'retentionEnabled', title: 'Retention again', description: 'The same key twice.' }
                ]
            }
        ]);

        await screen.findByLabelText('Toggle Retention');
        expect(screen.queryByLabelText('Toggle Banner again')).toBeNull();
        expect(screen.queryByLabelText('Toggle Retention again')).toBeNull();
    });

    it('leaves out a contributed section whose availability check refuses the loaded settings', async () => {
        const available = vi.fn().mockReturnValue(false);
        mountPage({ ...defaults, retentionEnabled: true }, [
            {
                id: 'retention',
                title: 'Retention',
                flags: [{ key: 'retentionEnabled', title: 'Keep raw events', description: 'Keeps the raw events.' }],
                available
            }
        ]);

        await screen.findByLabelText('Toggle Read-only mode');
        expect(available).toHaveBeenCalledWith(expect.objectContaining({ retentionEnabled: true }));
        expect(screen.queryByRole('heading', { name: 'Retention' })).toBeNull();
        expect(screen.queryByLabelText('Toggle Keep raw events')).toBeNull();
    });

    it('leaves out a contributed section that has no switch left', async () => {
        mountPage(defaults, [
            {
                id: 'audit',
                title: 'Audit',
                flags: [{ key: 'readOnly', title: 'Freeze', description: 'The same switch under another name.' }]
            }
        ]);

        await screen.findByLabelText('Toggle Read-only mode');
        expect(screen.queryByText('Audit')).toBeNull();
    });
});
