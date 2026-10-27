/********************************************************************************
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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { jsonResponse, stubFetch } from '../../support/fetch';
import { renderWithProviders } from '../../support/test-providers';
import { DeleteAllVersionsDialog } from '../../../../src/components/extension/extension-delete-all-versions-dialog';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, VersionTargetPlatforms } from '../../../../src/extension-registry-types';

const extension = { name: 'bar', namespace: 'foo', displayName: 'Bar Tools' } as unknown as Extension;

const versions: VersionTargetPlatforms[] = ['1.0.0', '2.0.0'].map(version => ({
    version,
    targetPlatforms: [{ targetPlatform: 'universal', active: true, removed: false }]
}));

// MUI's Dialog transition trips a jsdom getComputedStyle bug that every getByRole query
// walks into while the dialog is open - query the dialog's buttons by text instead.
const deleteButton = () => screen.getByText('Delete All Versions', { selector: 'button' });

/**
 * Renders the dialog with a real service answering `result`, so the delete travels the request
 * layer that decides whether a response body counts as an error.
 */
function renderDialog(result: unknown) {
    stubFetch(jsonResponse({ header: 'X-XSRF-TOKEN', value: 'token' }), jsonResponse(result));
    const service = new ExtensionRegistryService('https://open-vsx.org');
    const onDeleted = vi.fn();
    const onClose = vi.fn();
    const handleError = vi.fn();
    renderWithProviders(
        <DeleteAllVersionsDialog
            open
            onClose={onClose}
            extension={extension}
            versions={versions}
            onRemove={targetPlatformVersions =>
                service.deleteExtensions({ namespace: 'foo', extension: 'bar', targetPlatformVersions })
            }
            onDeleted={onDeleted}
        />,
        { mainContext: { handleError } }
    );
    return { onDeleted, onClose, handleError };
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('DeleteAllVersionsDialog', () => {
    // The registry deletes each version separately and aggregates the outcomes, so even a delete
    // that fully succeeds carries the (empty) error half of that result.
    it('closes and notifies the page when the empty error is the only one reported', async () => {
        const { onDeleted, onClose, handleError } = renderDialog({ success: 'Deleted 2 versions', error: '' });

        await userEvent.click(deleteButton());

        await waitFor(() => expect(onDeleted).toHaveBeenCalled());
        expect(onClose).toHaveBeenCalled();
        expect(handleError).not.toHaveBeenCalled();
    });

    it('reports a partial delete, where some versions were removed and others failed', async () => {
        const aggregate = { success: 'Deleted 1.0.0', error: 'Failed to delete 2.0.0' };
        const { onDeleted, onClose, handleError } = renderDialog(aggregate);

        await userEvent.click(deleteButton());

        await waitFor(() =>
            expect(handleError).toHaveBeenCalledWith(expect.objectContaining(aggregate), {
                title: 'Couldn’t delete the extension'
            })
        );
        expect(onDeleted).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });
});
