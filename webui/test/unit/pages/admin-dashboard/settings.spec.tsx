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
import { RuntimeSettingsPage } from '../../../../src/pages/admin-dashboard/settings';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Settings } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const settings = (overrides: Partial<Settings> = {}): Settings => ({
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    maxOverrideSize: Number.MAX_SAFE_INTEGER,
    ...overrides
});

const mountPage = (data: Settings, updateSettings = vi.fn().mockResolvedValue(data)) => {
    const admin = {
        getSettings: vi.fn().mockResolvedValue(data),
        updateSettings
    };
    renderWithProviders(<RuntimeSettingsPage />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('RuntimeSettingsPage', () => {
    it('shows the current default max extension size in MB', async () => {
        mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

        await waitFor(() => expect(screen.getByLabelText('Max extension size (MB)')).toHaveValue(512));
    });

    it('disables save once the typed size is not greater than zero', async () => {
        const user = userEvent.setup();
        mountPage(settings());

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
        mountPage(settings({ maxOverrideSize: 1024 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '2048');

        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
        expect(screen.getByText(/at most 1024 MB/i)).toBeInTheDocument();
    });

    /**
     * Flooring a ceiling that is not a whole number of MB understated it - a 1.5 MiB ceiling read "at
     * most 1 MB" even though up to 1.5 MB was actually valid.
     */
    it('does not floor a ceiling that is not a whole number of MB', async () => {
        const user = userEvent.setup();
        mountPage(settings({ maxOverrideSize: 1.5 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);
        await user.type(input, '2');

        expect(screen.getByText(/at most 1\.5 MB/i)).toBeInTheDocument();
    });

    // parseInt stopped at the decimal point, so this used to save 1 MB while the field still read
    // 1.5 - a limit nobody chose, applied silently.
    it('keeps a fractional MB value instead of truncating it', async () => {
        const user = userEvent.setup();
        const admin = mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

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
        const admin = mountPage(settings({ readOnly: true, maxExtensionSize: 512 * 1024 * 1024 }));

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
        const admin = mountPage(settings({ readOnly: false, maxExtensionSize: Number.MAX_SAFE_INTEGER + 1 }));

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
        mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await waitFor(() => expect(input).toBeEnabled());
        await user.clear(input);

        expect(input).toHaveValue(null);
        // empty is not a size, so there is nothing to save
        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    });

    it('converts the typed MB value to bytes and saves it', async () => {
        const user = userEvent.setup();
        const admin = mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

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
