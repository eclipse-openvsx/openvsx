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

import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../../../support/test-providers';
import { UserPublisherAgreement } from '../../../../../src/pages/user/profile/user-publisher-agreement';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { PageSettings } from '../../../../../src/page-settings';
import { UserData } from '../../../../../src/extension-registry-types';

const service = { serverUrl: 'https://open-vsx.org' } as ExtensionRegistryService;
const pageSettings = {
    publisherAgreement: { name: 'Open VSX' },
    urls: { extensionDefaultIcon: '', namespaceAccessInfo: '', publisherAgreement: '/api/publisher-agreement' }
} as PageSettings;

const user = (overrides: Partial<UserData> = {}): UserData => ({
    loginName: 'octocat',
    tokensUrl: '',
    createTokenUrl: '',
    publisherAgreement: { status: 'none', loginProvider: 'eclipse' },
    ...overrides
});

const render = (data: UserData) =>
    renderWithProviders(<UserPublisherAgreement user={data} />, { mainContext: { service, pageSettings } });

describe('UserPublisherAgreement', () => {
    it('asks for the login the server names before the agreement can be signed', () => {
        render(user());

        expect(screen.getByRole('link', { name: /log in with eclipse/i })).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/eclipse?redirect=%2F'
        );
        expect(screen.queryByRole('button', { name: /review & sign/i })).not.toBeInTheDocument();
    });

    it('asks the server to return to the page the login was started from', () => {
        renderWithProviders(<UserPublisherAgreement user={user()} />, {
            mainContext: { service, pageSettings },
            route: '/user-settings/profile?tab=agreement'
        });

        expect(screen.getByRole('link')).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/eclipse?redirect=%2Fuser-settings%2Fprofile%3Ftab%3Dagreement'
        );
    });

    it('follows a login provider other than eclipse', () => {
        render(user({ publisherAgreement: { status: 'none', loginProvider: 'acme' } }));

        expect(screen.getByRole('link')).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/acme?redirect=%2F'
        );
    });

    it('labels the login with the display name when the server provides one', () => {
        render(user({ publisherAgreement: { status: 'none', loginProvider: 'acme', loginProviderName: 'Acme Inc' } }));

        expect(screen.getByRole('link', { name: 'Log in with Acme Inc' })).toBeInTheDocument();
    });

    it('offers signing once the user holds the named login', () => {
        render(
            user({
                additionalLogins: [{ loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'eclipse' }]
            })
        );

        expect(screen.getByRole('button', { name: /review & sign/i })).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('offers signing straight away when the server names no additional login', () => {
        render(user({ publisherAgreement: { status: 'none' } }));

        expect(screen.getByRole('button', { name: /review & sign/i })).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('shows the signed state without a login', () => {
        render(user({ publisherAgreement: { status: 'signed', loginProvider: 'eclipse' } }));

        expect(screen.getByText('Publisher Agreement signed')).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('offers no way to sign after a revocation', () => {
        render(user({ publisherAgreement: { status: 'revoked' } }));

        expect(screen.getByText('Publisher Agreement revoked')).toBeInTheDocument();
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('says the agreement changed when the signature is outdated', () => {
        render(user({ publisherAgreement: { status: 'outdated' } }));

        expect(screen.getByText('Publisher Agreement updated')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /review & sign/i })).toBeInTheDocument();
    });
});
