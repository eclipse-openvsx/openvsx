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
import { MainContext } from '../../../../../src/context';
import { testUser } from '../../../support/trusted-publishing';
import { UserSettingsTokens } from '../../../../../src/pages/user/tokens/user-settings-tokens';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { PageSettings } from '../../../../../src/page-settings';
import { PersonalAccessToken } from '../../../../../src/extension-registry-types';

const token: PersonalAccessToken = {
    id: 1,
    description: 'publishing token',
    createdTimestamp: '2026-01-01T00:00:00Z',
    deleteTokenUrl: ''
};

function renderTokens(mainContext: Partial<MainContext> = {}, tokens: PersonalAccessToken[] = [token]) {
    const deleteAccessToken = vi.fn().mockResolvedValue(undefined);
    const getAccessTokens = vi.fn().mockResolvedValue(tokens);
    const service = {
        getAccessTokens,
        deleteAccessToken,
        getTrustedPublishingStatus: vi.fn().mockResolvedValue({ enabled: false, allowed: false })
    } as unknown as ExtensionRegistryService;
    renderWithProviders(<UserSettingsTokens />, {
        mainContext: { service, user: testUser, pageSettings: {} as PageSettings, ...mainContext }
    });
    return { deleteAccessToken, getAccessTokens };
}

describe('UserSettingsTokens — unsigned publisher agreement', () => {
    const unsigned = { ...testUser, publisherAgreement: { status: 'none' as const } };

    it('blocks token creation and points at the profile to sign', async () => {
        renderTokens({
            user: unsigned,
            pageSettings: { publisherAgreement: { name: 'Eclipse' } } as PageSettings
        });

        expect(await screen.findByText('Publisher agreement required')).toBeInTheDocument();
        expect(screen.getByText(/signed the Eclipse Publisher Agreement/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Sign it on your profile/ })).toHaveAttribute(
            'href',
            '/user-settings/profile'
        );
        // No list and no "Generate new token": the page is only the explanation.
        expect(screen.queryByText('Generate new token')).not.toBeInTheDocument();
    });

    it('names the agreement without an empty gap when the deployment does not title it', async () => {
        renderTokens({ user: unsigned });

        expect(await screen.findByText(/signed the Publisher Agreement/)).toBeInTheDocument();
    });

    it('offers the contact address only when one is configured', async () => {
        renderTokens({
            user: unsigned,
            pageSettings: { publisherAgreement: { email: 'help@example.test' } } as PageSettings
        });

        expect(await screen.findByText('help@example.test')).toBeInTheDocument();
    });
});

describe('UserSettingsTokens', () => {
    it('revokes a token only after the confirmation is accepted', async () => {
        const { deleteAccessToken } = renderTokens();

        await userEvent.click(await screen.findByLabelText('Revoke token publishing token'));

        expect(await screen.findByText('Revoke access token')).toBeInTheDocument();
        expect(deleteAccessToken).not.toHaveBeenCalled();

        await userEvent.click(screen.getByText('Revoke'));
        await waitFor(() => expect(deleteAccessToken).toHaveBeenCalledWith(expect.anything(), token));
    });

    it('keeps the token when the confirmation is dismissed', async () => {
        const { deleteAccessToken } = renderTokens();

        await userEvent.click(await screen.findByLabelText('Revoke token publishing token'));
        await userEvent.click(await screen.findByText('Cancel'));

        await waitFor(() => expect(screen.queryByText('Revoke access token')).not.toBeInTheDocument());
        expect(deleteAccessToken).not.toHaveBeenCalled();
    });
});

describe('UserSettingsTokens — token scope', () => {
    it('shows the namespace scope', async () => {
        renderTokens({}, [{ ...token, scopeNamespace: 'foo' }]);

        expect(await screen.findByText('Scope: foo')).toBeInTheDocument();
    });

    it('shows the extension scope as namespace.extension', async () => {
        renderTokens({}, [{ ...token, scopeNamespace: 'foo', scopeExtension: 'bar' }]);

        expect(await screen.findByText('Scope: foo.bar')).toBeInTheDocument();
    });

    it('marks a publishing only token, with its scope if it has one', async () => {
        renderTokens({}, [{ ...token, publishingOnly: true, scopeNamespace: 'foo' }]);

        expect(await screen.findByText('Scope: foo · Publishing only')).toBeInTheDocument();
    });

    it('marks a publishing only token that has no scope', async () => {
        renderTokens({}, [{ ...token, publishingOnly: true }]);

        expect(await screen.findByText('Publishing only')).toBeInTheDocument();
    });

    it('shows neither for an unrestricted token', async () => {
        renderTokens();

        await screen.findByText('publishing token');
        expect(screen.queryByText(/Scope:/)).not.toBeInTheDocument();
        expect(screen.queryByText('Publishing only')).not.toBeInTheDocument();
    });
});
