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
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SizeOverrideFormDialog } from '../../../../src/pages/admin-dashboard/size-overrides/size-override-form-dialog';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { renderWithProviders } from '../../support/test-providers';

const namespaceResult = (extensions: Record<string, string> = {}, verified = true) => ({
    name: 'foo',
    extensions,
    verified
});

// not named render*, so the testing-library naming rule does not treat the stubs it returns as a
// render result
const mountDialog = (
    getNamespace = vi.fn(),
    onSubmit = vi.fn().mockResolvedValue(undefined),
    maxOverrideSize?: number
) => {
    const admin = { getNamespace };
    renderWithProviders(
        <SizeOverrideFormDialog
            open
            sizeOverride={undefined}
            maxOverrideSize={maxOverrideSize}
            onClose={vi.fn()}
            onSubmit={onSubmit}
        />,
        { mainContext: { service: { admin } as unknown as ExtensionRegistryService } }
    );
    return { admin, onSubmit };
};

/** Types a namespace and waits for the debounced check to report it as verified. */
const enterVerifiedNamespace = async (user: ReturnType<typeof userEvent.setup>, name = 'foo') => {
    await user.type(screen.getByLabelText(/namespace/i), name);
    await screen.findByText(/verified namespace/i);
};

describe('SizeOverrideFormDialog', () => {
    it('converts the typed value and unit to bytes on submit', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog(vi.fn().mockResolvedValue(namespaceResult()));

        await enterVerifiedNamespace(user);
        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '100');
        await user.click(screen.getByRole('button', { name: /create/i }));

        await waitFor(() =>
            expect(onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({ namespace: 'foo', maxSize: 100 * 1024 * 1024 })
            )
        );
    });

    /**
     * The name is trimmed before it is checked, so submitting the raw field would ask the server to
     * save an override for a namespace it would not find - after the form said it was verified.
     */
    it('submits the same name it checked, not the raw field', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue(namespaceResult());
        const { onSubmit } = mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), '  foo  ');
        await screen.findByText(/verified namespace/i);
        await user.click(screen.getByRole('button', { name: /create/i }));

        expect(getNamespace).toHaveBeenCalledWith(expect.anything(), 'foo');
        await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ namespace: 'foo' })));
    });

    it('keeps a fractional size instead of truncating it', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog(vi.fn().mockResolvedValue(namespaceResult()));

        await enterVerifiedNamespace(user);
        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '1.5');
        await user.click(screen.getByRole('button', { name: /create/i }));

        await waitFor(() =>
            expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ maxSize: 1.5 * 1024 * 1024 }))
        );
    });

    /**
     * The server refuses an override above ovsx.publishing.max-override-size, so saying so here beats
     * letting the admin discover it from the submit's error response.
     */
    it('refuses to submit a size above the override ceiling', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog(
            vi.fn().mockResolvedValue(namespaceResult()),
            vi.fn().mockResolvedValue(undefined),
            100 * 1024 * 1024
        );

        await enterVerifiedNamespace(user);
        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '200');

        expect(screen.getByText(/exceeds the maximum of 100 mb/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('offers the namespace extensions to choose from once it checks out', async () => {
        const user = userEvent.setup();
        mountDialog(vi.fn().mockResolvedValue(namespaceResult({ bar: 'u', baz: 'u' })));

        await enterVerifiedNamespace(user);
        await user.click(screen.getByLabelText(/extension/i));

        expect(await screen.findByText('bar')).toBeInTheDocument();
        expect(screen.getByText('baz')).toBeInTheDocument();
    });

    /**
     * The check runs on its own as the admin types; nothing has to be pressed first. Before it has
     * cleared the namespace there is nothing to create an override for.
     */
    it('will not submit until the namespace checks out', async () => {
        const user = userEvent.setup();
        mountDialog(vi.fn().mockResolvedValue(namespaceResult()));

        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();

        await screen.findByText(/verified namespace/i);
        expect(screen.getByRole('button', { name: /create/i })).toBeEnabled();
    });

    /**
     * The server refuses an override on an unverified namespace, so saying so here beats letting the
     * admin fill the form in and discover it from the submit response.
     */
    it('rejects an unverified namespace without asking the server to save', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog(vi.fn().mockResolvedValue(namespaceResult({}, false)));

        await user.type(screen.getByLabelText(/namespace/i), 'foo');

        expect(await screen.findByText(/not verified/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('reports an unknown namespace instead of enabling submit', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog(vi.fn().mockRejectedValue({ error: 'Namespace not found: nope' }));

        await user.type(screen.getByLabelText(/namespace/i), 'nope');

        expect(await screen.findByText(/not found/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    /** One request per pause in typing, not one per keystroke. */
    it('checks once for a name typed in one go', async () => {
        const user = userEvent.setup();
        const { admin } = mountDialog(vi.fn().mockResolvedValue(namespaceResult()));

        await enterVerifiedNamespace(user, 'foo');

        expect(admin.getNamespace).toHaveBeenCalledTimes(1);
        expect(admin.getNamespace).toHaveBeenCalledWith(expect.anything(), 'foo');
    });

    /**
     * Looking up one namespace and typing another before the answer lands must not confirm whichever
     * name is in the box, or an override could be created for a namespace nobody checked.
     */
    it('ignores an answer that arrives after the namespace changed', async () => {
        const user = userEvent.setup();
        const releases: ((value: unknown) => void)[] = [];
        const getNamespace = vi.fn().mockImplementation(
            () =>
                new Promise(resolve => {
                    releases.push(resolve);
                })
        );
        mountDialog(getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await waitFor(() => expect(getNamespace).toHaveBeenCalledTimes(1));
        await user.type(screen.getByLabelText(/namespace/i), 'bar');

        await act(async () => {
            releases[0](namespaceResult({ one: 'u' }));
        });

        // the stale answer must not confirm the name now in the box
        expect(screen.getByRole('button', { name: /create/i })).toBeDisabled();
    });

    /**
     * Closing mid-save lets a second override be opened, and the first request's own onClose then
     * shuts that one - with the state of an operation the admin has already moved on from. Cancel is
     * asserted here; the backdrop and Escape paths are closed by passing no onClose to the Dialog
     * while saving, which cannot be asserted without reaching for the backdrop node directly.
     */
    it('locks Cancel while a save is in flight', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        // never settles: the save is still pending when the dismissal is attempted
        const onSubmit = vi.fn().mockImplementation(() => new Promise(() => undefined));
        renderWithProviders(
            <SizeOverrideFormDialog open sizeOverride={undefined} onClose={onClose} onSubmit={onSubmit} />,
            {
                mainContext: {
                    service: {
                        admin: { getNamespace: vi.fn().mockResolvedValue(namespaceResult()) }
                    } as unknown as ExtensionRegistryService
                }
            }
        );

        await enterVerifiedNamespace(user);
        await user.click(screen.getByRole('button', { name: /create/i }));
        await waitFor(() => expect(onSubmit).toHaveBeenCalled());

        expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
        expect(onClose).not.toHaveBeenCalled();
    });

    /** Editing cannot change the scope, so the fixed namespace is not re-checked. */
    it('does not check the namespace when editing an existing override', async () => {
        const getNamespace = vi.fn();
        renderWithProviders(
            <SizeOverrideFormDialog
                open
                sizeOverride={{ id: 1, namespace: 'foo', extension: 'bar', maxSize: 1024 * 1024 }}
                onClose={vi.fn()}
                onSubmit={vi.fn()}
            />,
            { mainContext: { service: { admin: { getNamespace } } as unknown as ExtensionRegistryService } }
        );

        expect(await screen.findByRole('button', { name: /update/i })).toBeEnabled();
        expect(getNamespace).not.toHaveBeenCalled();
    });
});
