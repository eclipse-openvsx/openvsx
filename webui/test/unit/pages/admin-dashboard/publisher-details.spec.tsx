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
import { renderWithProviders } from '../../support/test-providers';
import { PublisherDetails } from '../../../../src/pages/admin-dashboard/publisher-details';
import { ExtensionRegistryService, AdminService } from '../../../../src/extension-registry-service';
import { PublisherInfo, UserData, UserRelationships } from '../../../../src/extension-registry-types';

function entry(namespaces: UserRelationships['namespaces'] = []): UserRelationships {
    return {
        user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' },
        namespaces
    };
}

const admin: UserData = { loginName: 'root', tokensUrl: '', createTokenUrl: '', role: 'admin' };
const publisherManager: UserData = {
    loginName: 'sam',
    tokensUrl: '',
    createTokenUrl: '',
    permissions: ['manage_publishers']
};

function serviceReturning(publisherAgreement?: UserData['publisherAgreement']): ExtensionRegistryService {
    const publisherInfo: PublisherInfo = {
        user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github', publisherAgreement },
        extensions: [],
        activeAccessTokenNum: 0
    };
    return {
        serverUrl: 'https://open-vsx.org',
        admin: { getPublisherInfo: () => Promise.resolve(publisherInfo) } as unknown as AdminService
    } as ExtensionRegistryService;
}

describe('PublisherDetails — publisher agreement chip', () => {
    // Regression: the backend falls back to 'none' whenever it cannot confirm the agreement
    // status (e.g. a blocked Eclipse profile) rather than reporting a status the frontend has
    // no rendering for, so 'none' is the only "nothing to show" state this chip needs to cover.
    it('renders the "Not signed" chip when the agreement status is "none"', async () => {
        renderWithProviders(<PublisherDetails entry={entry()} />, {
            mainContext: { service: serviceReturning({ status: 'none' }) }
        });

        expect(await screen.findByText('Publisher agreement: Not signed')).toBeInTheDocument();
    });
});

/**
 * A publisher's namespaces are legitimately on show to anyone who may open this page, but the
 * namespace admin page behind them needs its own permission - so the link has to lead somewhere the
 * viewer can actually go.
 */
describe('PublisherDetails - cross-links out of the page', () => {
    const namespaces = [{ name: 'redhat' }] as UserRelationships['namespaces'];

    it('links a namespace to its admin page for a user who may open it', async () => {
        renderWithProviders(<PublisherDetails entry={entry(namespaces)} />, {
            mainContext: { service: serviceReturning(), user: admin }
        });

        const link = await screen.findByRole('link', { name: 'redhat' });
        expect(link).toHaveAttribute('href', '/admin-dashboard/namespaces/redhat');
    });

    it('links a namespace to its public page for a user without the namespaces permission', async () => {
        renderWithProviders(<PublisherDetails entry={entry(namespaces)} />, {
            mainContext: { service: serviceReturning(), user: publisherManager }
        });

        const link = await screen.findByRole('link', { name: 'redhat' });
        expect(link).toHaveAttribute('href', '/namespace/redhat');
    });
});
