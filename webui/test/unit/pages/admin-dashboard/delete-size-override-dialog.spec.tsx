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
import { DeleteSizeOverrideDialog } from '../../../../src/pages/admin-dashboard/size-overrides/delete-size-override-dialog';
import { renderWithProviders } from '../../support/test-providers';

const sizeOverride = { id: 7, namespace: 'foo', maxSize: 100 };

describe('DeleteSizeOverrideDialog', () => {
    it('names the scope it is about to remove', () => {
        renderWithProviders(
            <DeleteSizeOverrideDialog
                open
                sizeOverride={{ ...sizeOverride, extension: 'bar' }}
                onClose={vi.fn()}
                onConfirm={vi.fn()}
            />
        );

        expect(screen.getByText('foo.bar')).toBeInTheDocument();
    });

    /**
     * Closing mid-delete lets another row be opened, and the first request's own onClose then shuts
     * that confirmation - the admin would be confirming one deletion and watching a different one
     * disappear. The buttons are asserted here; the backdrop and Escape paths are closed by passing
     * no onClose to the Dialog while loading, which cannot be asserted without reaching for the
     * backdrop node directly.
     */
    it('locks its actions while the delete is in flight', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        // never settles: the request is still pending when the dismissal is attempted
        const onConfirm = vi.fn().mockImplementation(() => new Promise(() => undefined));
        renderWithProviders(
            <DeleteSizeOverrideDialog open sizeOverride={sizeOverride} onClose={onClose} onConfirm={onConfirm} />
        );

        await user.click(screen.getByRole('button', { name: /^delete$/i }));
        await waitFor(() => expect(onConfirm).toHaveBeenCalled());

        expect(screen.getByRole('button', { name: /^delete$/i })).toBeDisabled();
        expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
        expect(onClose).not.toHaveBeenCalled();
    });
});
