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
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { namespaceDetails, testNamespace } from '../../support/user-settings';
import { NamespaceDetailsForm } from '../../../../src/components/namespace/namespace-details-form';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { NamespaceDetails } from '../../../../src/extension-registry-types';

async function openForm(details: NamespaceDetails) {
    const getNamespaceDetails = vi.fn().mockResolvedValue(details);
    renderWithProviders(<NamespaceDetailsForm namespace={testNamespace({ detailsUrl: '/details' })} />, {
        mainContext: { service: { getNamespaceDetails } as unknown as ExtensionRegistryService }
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    return { user, saveButton: screen.getByRole('button', { name: 'Save namespace details' }) };
}

describe('NamespaceDetailsForm', () => {
    it('keeps Save disabled until a field changes', async () => {
        const { saveButton } = await openForm(namespaceDetails());

        expect(saveButton).toBeDisabled();
    });

    it('enables Save when only a social handle changes', async () => {
        const { user, saveButton } = await openForm(namespaceDetails());

        await user.type(screen.getByLabelText('GitHub'), 'octocat');

        expect(saveButton).toBeEnabled();
    });

    it('disables Save again when a social handle is typed and then cleared', async () => {
        const { user, saveButton } = await openForm(namespaceDetails());

        await user.type(screen.getByLabelText('GitHub'), 'octocat');
        await user.clear(screen.getByLabelText('GitHub'));

        expect(saveButton).toBeDisabled();
    });

    it('disables Save again when a top-level field is typed and then cleared', async () => {
        const { user, saveButton } = await openForm(namespaceDetails());

        await user.type(screen.getByLabelText('Description'), 'About foo');
        await user.clear(screen.getByLabelText('Description'));

        expect(saveButton).toBeDisabled();
    });

    it('ignores server-computed fields the form does not send', async () => {
        const { saveButton } = await openForm(namespaceDetails({ logoBytes: 'abc', extensions: [] }));

        expect(saveButton).toBeDisabled();
    });

    it('disables Save again when an existing handle is edited back to its saved value', async () => {
        const { user, saveButton } = await openForm(
            namespaceDetails({ socialLinks: { github: 'https://github.com/octocat' } })
        );

        const github = screen.getByLabelText('GitHub');
        await user.type(github, 'x');
        expect(saveButton).toBeEnabled();
        await user.type(github, '{Backspace}');

        expect(saveButton).toBeDisabled();
    });
});
