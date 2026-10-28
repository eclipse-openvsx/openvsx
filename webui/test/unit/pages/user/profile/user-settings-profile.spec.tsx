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
import { UserSettingsProfile } from '../../../../../src/pages/user/profile/user-settings-profile';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { PageSettings } from '../../../../../src/page-settings';
import { UserData } from '../../../../../src/extension-registry-types';

const service = { serverUrl: 'https://open-vsx.org' } as ExtensionRegistryService;
const pageSettings = { publisherAgreement: { name: 'Open VSX' } } as PageSettings;

const asAdmin = (status: NonNullable<UserData['publisherAgreement']>['status']) => {
    const user: UserData = {
        loginName: 'octocat',
        tokensUrl: '',
        createTokenUrl: '',
        publisherAgreement: { status }
    };
    renderWithProviders(<UserSettingsProfile user={user} isAdmin />, { mainContext: { service, pageSettings } });
};

describe('UserSettingsProfile — publisher agreement seen by an admin', () => {
    it.each([
        ['none', 'octocat has not signed the Open VSX Publisher Agreement.'],
        ['signed', 'octocat has signed the Open VSX Publisher Agreement.'],
        ['outdated', 'octocat has signed an outdated version of the Open VSX Publisher Agreement.'],
        ['revoked', 'octocat has a revoked signature of the Open VSX Publisher Agreement.']
    ] as const)('states a %s agreement as a sentence', (status, sentence) => {
        asAdmin(status);

        expect(screen.getByText(sentence)).toBeInTheDocument();
    });
});
