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
import { testNamespace } from '../../../support/user-settings';
import { GenerateAccessTokenDialog } from '../../../../../src/pages/user/tokens/generate-access-token-dialog';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';

function renderDialog() {
    const createAccessToken = vi.fn().mockResolvedValue({ id: 1, value: 'the-token' });
    const handleTokenGenerated = vi.fn();
    const getNamespaces = vi.fn().mockResolvedValue([testNamespace({ name: 'foo' }), testNamespace({ name: 'baz' })]);
    const getPublicNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: { bar: 'u1', qux: 'u2' } });
    const service = { createAccessToken, getNamespaces, getPublicNamespace } as unknown as ExtensionRegistryService;
    renderWithProviders(<GenerateAccessTokenDialog handleTokenGenerated={handleTokenGenerated} />, {
        mainContext: { service, user: testUser }
    });
    return { createAccessToken, getPublicNamespace, handleTokenGenerated };
}

async function openDialog() {
    await userEvent.click(screen.getByRole('button', { name: 'Generate new token' }));
}

async function selectExtension(name: string) {
    await userEvent.click(screen.getByRole('combobox', { name: 'Extension (optional)' }));
    await userEvent.click(await screen.findByRole('option', { name }));
}

async function selectNamespace(name: string) {
    await userEvent.click(screen.getByRole('combobox', { name: 'Namespace (optional)' }));
    await userEvent.click(await screen.findByRole('option', { name }));
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
        await selectNamespace('foo');
        await selectExtension('bar');
        await userEvent.click(screen.getByRole('checkbox', { name: 'Publishing only' }));
        await userEvent.click(screen.getByRole('button', { name: 'Generate Token' }));

        await waitFor(() => expect(createAccessToken).toHaveBeenCalledOnce());
        expect(createAccessToken.mock.calls[0][3]).toEqual({
            namespace: 'foo',
            extension: 'bar',
            publishingOnly: true
        });
    });

    it('offers only the namespaces the user is a member of', async () => {
        renderDialog();

        await openDialog();
        await userEvent.click(screen.getByRole('combobox', { name: 'Namespace (optional)' }));

        expect(await screen.findAllByRole('option')).toHaveLength(2);
        expect(screen.getByRole('option', { name: 'baz' })).toBeInTheDocument();
    });

    it('offers the active extensions of the chosen namespace', async () => {
        const { getPublicNamespace } = renderDialog();

        await openDialog();
        await selectNamespace('foo');
        await userEvent.click(screen.getByRole('combobox', { name: 'Extension (optional)' }));

        expect(getPublicNamespace.mock.calls[0][1]).toBe('foo');
        expect(await screen.findAllByRole('option')).toHaveLength(2);
        expect(screen.getByRole('option', { name: 'qux' })).toBeInTheDocument();
    });

    it('does not allow an extension scope without a namespace', async () => {
        renderDialog();

        await openDialog();

        expect(screen.getByLabelText('Extension (optional)')).toBeDisabled();
        await selectNamespace('foo');
        expect(screen.getByLabelText('Extension (optional)')).toBeEnabled();
    });

    it('drops the extension when the namespace is cleared', async () => {
        const { createAccessToken } = renderDialog();

        await openDialog();
        await selectNamespace('foo');
        await selectExtension('bar');
        await userEvent.click(screen.getAllByTitle('Clear')[0]);
        expect(screen.getByLabelText('Extension (optional)')).toHaveValue('');
        await userEvent.click(screen.getByRole('button', { name: 'Generate Token' }));

        await waitFor(() => expect(createAccessToken).toHaveBeenCalledOnce());
        expect(createAccessToken.mock.calls[0][3]).toEqual({
            namespace: undefined,
            extension: undefined,
            publishingOnly: false
        });
    });

    it('starts afresh the next time it is opened', async () => {
        renderDialog();

        await openDialog();
        await selectNamespace('foo');
        await userEvent.click(screen.getByRole('checkbox', { name: 'Publishing only' }));
        await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
        await waitFor(() => expect(screen.queryByLabelText('Namespace (optional)')).not.toBeInTheDocument());
        await openDialog();

        expect(screen.getByLabelText('Namespace (optional)')).toHaveValue('');
        expect(screen.getByRole('checkbox', { name: 'Publishing only' })).not.toBeChecked();
    });

    it('blocks generation while the typed text is not a listed option', async () => {
        const { createAccessToken } = renderDialog();

        await openDialog();
        await userEvent.type(screen.getByRole('combobox', { name: 'Namespace (optional)' }), 'nope');
        await userEvent.click(screen.getByLabelText('Description (optional)'));

        expect(screen.getByText('Select a namespace from the list or clear the field.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Generate Token' })).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Description (optional)'), '{Enter}');
        expect(createAccessToken).not.toHaveBeenCalled();

        await userEvent.clear(screen.getByRole('combobox', { name: 'Namespace (optional)' }));
        expect(screen.getByRole('button', { name: 'Generate Token' })).toBeEnabled();
    });

    it('blocks generation while the typed extension is not a listed option', async () => {
        renderDialog();

        await openDialog();
        await selectNamespace('foo');
        await userEvent.type(screen.getByRole('combobox', { name: 'Extension (optional)' }), 'nope');

        expect(screen.getByRole('button', { name: 'Generate Token' })).toBeDisabled();
        await userEvent.click(screen.getAllByTitle('Clear')[0]);
        expect(screen.getByRole('button', { name: 'Generate Token' })).toBeEnabled();
    });
});
