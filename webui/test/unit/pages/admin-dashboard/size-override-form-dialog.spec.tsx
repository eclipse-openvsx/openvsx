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
import { SizeOverrideFormDialog } from '../../../../src/pages/admin-dashboard/size-overrides/size-override-form-dialog';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { renderWithProviders } from '../../support/test-providers';

// not named render*, so the testing-library naming rule does not treat the stubs it returns as a
// render result
const mountDialog = (getNamespace = vi.fn(), onSubmit = vi.fn().mockResolvedValue(undefined)) => {
    const admin = { getNamespace };
    renderWithProviders(
        <SizeOverrideFormDialog open sizeOverride={undefined} onClose={vi.fn()} onSubmit={onSubmit} />,
        { mainContext: { service: { admin } as unknown as ExtensionRegistryService } }
    );
    return { admin, onSubmit };
};

describe('SizeOverrideFormDialog', () => {
    it('converts the typed value and unit to bytes on submit', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: {} });
        const { onSubmit } = mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());

        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '100');
        await user.click(screen.getByRole('button', { name: /create/i }));

        await waitFor(() =>
            expect(onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({ namespace: 'foo', maxSize: 100 * 1024 * 1024 })
            )
        );
    });

    it('keeps a fractional size instead of truncating it', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: {} });
        const { onSubmit } = mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());

        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '1.5');
        await user.click(screen.getByRole('button', { name: /create/i }));

        await waitFor(() =>
            expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ maxSize: 1.5 * 1024 * 1024 }))
        );
    });

    it('offers the looked-up namespace extensions to choose from', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: { bar: 'u', baz: 'u' } });
        mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());

        await user.click(screen.getByLabelText(/extension/i));

        expect(await screen.findByText('bar')).toBeInTheDocument();
        expect(screen.getByText('baz')).toBeInTheDocument();
    });

    /**
     * Looking up one namespace and typing another before the answer lands used to confirm whichever
     * name was in the box, so an override could be created for a namespace nobody looked up.
     */
    it('ignores a lookup answer that arrives after the namespace changed', async () => {
        const user = userEvent.setup();
        let release: (value: unknown) => void = () => undefined;
        const getNamespace = vi.fn().mockImplementation(
            () =>
                new Promise(resolve => {
                    release = resolve;
                })
        );
        mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await user.type(screen.getByLabelText(/namespace/i), 'bar');

        release({ name: 'foo', extensions: { one: 'u' } });
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());

        // the stale answer must not confirm the name now in the box
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
    });

    /**
     * The superseded request skips its own reset, so reopening has to clear the in-progress flag -
     * otherwise the button stays disabled for the life of the component and the dialog is unusable.
     */
    it('frees the lookup button when the dialog is reopened mid-lookup', async () => {
        const user = userEvent.setup();
        // never settles: the request is still in flight when the dialog closes
        const getNamespace = vi.fn().mockImplementation(() => new Promise(() => undefined));
        const props = { sizeOverride: undefined, onClose: vi.fn(), onSubmit: vi.fn() };
        const { rerender } = renderWithProviders(<SizeOverrideFormDialog open {...props} />, {
            mainContext: { service: { admin: { getNamespace } } as unknown as ExtensionRegistryService }
        });

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        expect(screen.getByRole('button', { name: /look up/i })).toBeDisabled();

        rerender(<SizeOverrideFormDialog open={false} {...props} />);
        rerender(<SizeOverrideFormDialog open {...props} />);
        await user.type(screen.getByLabelText(/namespace/i), 'bar');

        expect(screen.getByRole('button', { name: /look up/i })).toBeEnabled();
    });

    /**
     * Closing mid-save lets a second override be opened, and the first request's own onClose then
     * shuts that one - with the state of an operation the admin has already moved on from. Cancel is
     * asserted here; the backdrop and Escape paths are closed by passing no onClose to the Dialog
     * while saving, which cannot be asserted without reaching for the backdrop node directly.
     */
    it('locks Cancel while a save is in flight', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: {} });
        const onClose = vi.fn();
        // never settles: the save is still pending when the dismissal is attempted
        const onSubmit = vi.fn().mockImplementation(() => new Promise(() => undefined));
        renderWithProviders(
            <SizeOverrideFormDialog open sizeOverride={undefined} onClose={onClose} onSubmit={onSubmit} />,
            { mainContext: { service: { admin: { getNamespace } } as unknown as ExtensionRegistryService } }
        );

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /create/i }));
        await waitFor(() => expect(onSubmit).toHaveBeenCalled());

        expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('reports an unknown namespace instead of enabling submit', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockRejectedValue({ error: 'Namespace not found: nope' });
        const { onSubmit } = mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'nope');
        await user.click(screen.getByRole('button', { name: /look up/i }));

        expect(await screen.findByText(/not found/i)).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('will not submit before the namespace has been looked up', async () => {
        const user = userEvent.setup();
        mountDialog();

        await user.type(screen.getByLabelText(/namespace/i), 'foo');

        // typing a namespace is not enough: it has to be confirmed to exist first
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
    });
});
