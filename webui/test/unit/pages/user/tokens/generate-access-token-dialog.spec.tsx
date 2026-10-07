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

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../../support/test-providers';
import { testUser } from '../../../support/trusted-publishing';
import { GenerateAccessTokenDialog } from '../../../../../src/pages/user/tokens/generate-access-token-dialog';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';

function renderDialog() {
    const createAccessToken = vi.fn().mockResolvedValue({ id: 1, value: 'the-token' });
    const handleTokenGenerated = vi.fn();
    const service = { createAccessToken } as unknown as ExtensionRegistryService;
    renderWithProviders(<GenerateAccessTokenDialog handleTokenGenerated={handleTokenGenerated} />, {
        mainContext: { service, user: testUser }
    });
    return { createAccessToken, handleTokenGenerated };
}

async function openDialog() {
    await userEvent.click(screen.getByRole('button', { name: 'Generate new token' }));
}

describe('GenerateAccessTokenDialog', () => {
    it('generates an unrestricted token by default', async () => {
        const { createAccessToken } = renderDialog();

        await openDialog();
        await userEvent.click(screen.getByRole('button', { name: 'Generate Token' }));

        await waitFor(() => expect(createAccessToken).toHaveBeenCalledOnce());
        expect(createAccessToken.mock.calls[0][3]).toEqual({
            namespace: undefined,
            extension: undefined,
            publishingOnly: false
        });
    });

    it('passes the entered scope and the publishing only choice', async () => {
        const { createAccessToken } = renderDialog();

        await openDialog();
        await userEvent.type(screen.getByLabelText('Namespace (optional)'), '  foo ');
        await userEvent.type(screen.getByLabelText('Extension (optional)'), 'bar');
        await userEvent.click(screen.getByRole('checkbox', { name: 'Publishing only' }));
        await userEvent.click(screen.getByRole('button', { name: 'Generate Token' }));

        await waitFor(() => expect(createAccessToken).toHaveBeenCalledOnce());
        expect(createAccessToken.mock.calls[0][3]).toEqual({
            namespace: 'foo',
            extension: 'bar',
            publishingOnly: true
        });
    });

    it('does not allow an extension scope without a namespace', async () => {
        renderDialog();

        await openDialog();

        expect(screen.getByLabelText('Extension (optional)')).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Namespace (optional)'), 'foo');
        expect(screen.getByLabelText('Extension (optional)')).toBeEnabled();
    });

    it('starts afresh the next time it is opened', async () => {
        renderDialog();

        await openDialog();
        await userEvent.type(screen.getByLabelText('Namespace (optional)'), 'foo');
        await userEvent.click(screen.getByRole('checkbox', { name: 'Publishing only' }));
        await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
        await waitFor(() => expect(screen.queryByLabelText('Namespace (optional)')).not.toBeInTheDocument());
        await openDialog();

        expect(screen.getByLabelText('Namespace (optional)')).toHaveValue('');
        expect(screen.getByRole('checkbox', { name: 'Publishing only' })).not.toBeChecked();
    });
});
