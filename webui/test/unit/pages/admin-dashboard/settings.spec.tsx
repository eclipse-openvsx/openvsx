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
