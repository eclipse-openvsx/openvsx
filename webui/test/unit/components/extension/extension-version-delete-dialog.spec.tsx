/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { DeleteVersionDialog } from '../../../../src/components/extension/extension-version-delete-dialog';
import { Extension, VersionTargetPlatforms } from '../../../../src/extension-registry-types';

const extension = { name: 'bar', namespace: 'foo', displayName: 'Bar Tools' } as unknown as Extension;

const version: VersionTargetPlatforms = {
    version: '1.0.0',
    targetPlatforms: [{ targetPlatform: 'universal', removed: false }]
} as unknown as VersionTargetPlatforms;

// MUI's Dialog transition trips a jsdom getComputedStyle bug that every getByRole query
// walks into while the dialog is open - query the dialog's buttons by text instead.
const removeButton = () => screen.getByText('Remove', { selector: 'button' });

function renderDialog(onRemove: () => Promise<unknown>) {
    const onDeleted = vi.fn();
    const onClose = vi.fn();
    const handleError = vi.fn();
    renderWithProviders(
        <DeleteVersionDialog
            open
            onClose={onClose}
            extension={extension}
            version={version}
            onRemove={onRemove}
            onDeleted={onDeleted}
        />,
        { mainContext: { handleError } }
    );
    return { onDeleted, onClose, handleError };
}

describe('DeleteVersionDialog', () => {
    it('closes and notifies the page when the removal succeeds', async () => {
        const { onDeleted, onClose, handleError } = renderDialog(vi.fn().mockResolvedValue({ success: 'ok' }));

        await userEvent.click(removeButton());

        await waitFor(() => expect(onDeleted).toHaveBeenCalled());
        expect(onClose).toHaveBeenCalled();
        expect(handleError).not.toHaveBeenCalled();
    });

    it('reports a rejected removal instead of reporting success', async () => {
        const { onDeleted, onClose, handleError } = renderDialog(vi.fn().mockRejectedValue({ error: 'boom' }));

        await userEvent.click(removeButton());

        await waitFor(() =>
            expect(handleError).toHaveBeenCalledWith({ error: 'boom' }, { title: 'Couldn’t delete the version' })
        );
        expect(onDeleted).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('defers closing to the error dialog when the versions are stale (409)', async () => {
        const { onDeleted, onClose, handleError } = renderDialog(vi.fn().mockRejectedValue({ status: 409 }));

        await userEvent.click(removeButton());

        await waitFor(() => expect(handleError).toHaveBeenCalled());
        expect(onDeleted).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();

        // The page refreshes and the dialog closes only once the user acknowledges the error.
        handleError.mock.calls[0][1].onClose();
        expect(onDeleted).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
    });
});
