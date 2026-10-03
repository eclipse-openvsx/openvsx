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
const mountPage = (sizeOverrides: SizeOverride[]) => {
    const admin = {
        getSizeOverrides: vi.fn().mockResolvedValue({ sizeOverrides }),
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

    it('deletes the override whose row action was used', async () => {
        const user = userEvent.setup();
        const admin = mountPage([{ id: 7, namespace: 'foo', maxSize: 100 }]);

        await user.click(await screen.findByTitle('Delete size override'));
        await user.click(await screen.findByRole('button', { name: /^delete$/i }));

        await waitFor(() => expect(admin.deleteSizeOverride).toHaveBeenCalledWith(7));
    });
});
