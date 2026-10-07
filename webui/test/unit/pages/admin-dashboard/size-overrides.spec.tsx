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
import { SizeOverrides } from '../../../../src/pages/admin-dashboard/size-overrides/size-overrides';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { SizeOverride } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (sizeOverrides: SizeOverride[], maxOverrideSize = Number.MAX_SAFE_INTEGER) => {
    const admin = {
        getSizeOverrides: vi.fn().mockResolvedValue({ sizeOverrides, maxOverrideSize }),
        createSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        updateSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        deleteSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        getNamespace: vi.fn().mockResolvedValue({ name: 'foo', extensions: { bar: 'url' } })
    };
    renderWithProviders(<SizeOverrides />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('SizeOverrides', () => {
    it('lists a namespace-wide override with a placeholder for the extension', async () => {
        mountPage([{ id: 1, namespace: 'foo', maxSize: 100 * 1024 * 1024 }]);

        expect(await screen.findByText('foo')).toBeInTheDocument();
        expect(screen.getByText('All extensions')).toBeInTheDocument();
        expect(screen.getByText('100 MB')).toBeInTheDocument();
    });

    it('lists an extension-scoped override with its extension name', async () => {
        mountPage([{ id: 2, namespace: 'foo', extension: 'bar', maxSize: 1024 * 1024 }]);

        expect(await screen.findByText('bar')).toBeInTheDocument();
        expect(screen.getByText('1 MB')).toBeInTheDocument();
    });

    it('shows an empty state when there are no overrides', async () => {
        mountPage([]);

        expect(await screen.findByText(/no size overrides are configured/i)).toBeInTheDocument();
    });

    // An empty list and a failed load look identical in the data; only one of them justifies telling
    // an admin that nothing is configured.
    it('does not claim there are no overrides when the list failed to load', async () => {
        renderWithProviders(<SizeOverrides />, {
            mainContext: {
                service: {
                    admin: { getSizeOverrides: vi.fn().mockRejectedValue({ error: 'boom' }) }
                } as unknown as ExtensionRegistryService
            }
        });

        expect(await screen.findByText(/boom/i)).toBeInTheDocument();
        expect(screen.queryByText(/no size overrides are configured/i)).not.toBeInTheDocument();
    });

    /**
     * A refetch that fails still has the rows from the load that succeeded. Blanking the page over a
     * failed background refresh throws away data the admin can still use, and the alert alone leaves
     * nothing behind once it is dismissed.
     */
    it('keeps showing the rows when a refetch fails after a change', async () => {
        const user = userEvent.setup();
        const admin = {
            getSizeOverrides: vi
                .fn()
                .mockResolvedValueOnce({ sizeOverrides: [{ id: 7, namespace: 'foo', maxSize: 100 }] })
                .mockRejectedValue({ error: 'boom' }),
            deleteSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
            getNamespace: vi.fn()
        };
        renderWithProviders(<SizeOverrides />, {
            mainContext: { service: { admin } as unknown as ExtensionRegistryService }
        });

        expect(await screen.findByText('foo')).toBeInTheDocument();

        await user.click(await screen.findByTitle('Delete size override'));
        await user.click(await screen.findByRole('button', { name: /^delete$/i }));

        expect(await screen.findByText(/boom/i)).toBeInTheDocument();
        expect(screen.getByText('foo')).toBeInTheDocument();
    });

    /**
     * The ceiling has to come from this page's own query (authorized for manage_extensions), not from
     * /admin/settings - that endpoint requires manage_settings, a permission this page's own users may
     * not hold, which silently dropped the client-side validation below for them.
     */
    it('validates a new override against the ceiling from its own query', async () => {
        const user = userEvent.setup();
        mountPage([], 100 * 1024 * 1024);

        await user.click(screen.getByRole('button', { name: /create override/i }));
        await user.type(await screen.findByLabelText(/namespace/i), 'foo');
        await screen.findByText(/verified namespace/i);
        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '200');

        expect(await screen.findByText(/exceeds the maximum of 100 mb/i)).toBeInTheDocument();
    });

    it('deletes the override whose row action was used', async () => {
        const user = userEvent.setup();
        const admin = mountPage([{ id: 7, namespace: 'foo', maxSize: 100 }]);

        await user.click(await screen.findByTitle('Delete size override'));
        await user.click(await screen.findByRole('button', { name: /^delete$/i }));

        await waitFor(() => expect(admin.deleteSizeOverride).toHaveBeenCalledWith(7));
    });
});
