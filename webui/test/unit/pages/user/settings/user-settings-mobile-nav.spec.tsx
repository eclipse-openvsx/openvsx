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
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../../support/test-providers';
import { disabledStatus, enabledStatus, testUser } from '../../../support/trusted-publishing';
import { settingsServiceStub } from '../../../support/user-settings';
import { UserSettingsMobileNav } from '../../../../../src/pages/user/settings/user-settings-mobile-nav';

describe('UserSettingsMobileNav', () => {
    it('renders nothing without a logged-in user', () => {
        const { service } = settingsServiceStub();
        const { container } = renderWithProviders(<UserSettingsMobileNav />, { mainContext: { service } });

        expect(container).toBeEmptyDOMElement();
    });

    it('always offers a Namespaces pill, since namespaces are not one of the tabs', async () => {
        const { service } = settingsServiceStub({ status: disabledStatus });
        renderWithProviders(<UserSettingsMobileNav />, { mainContext: { service, user: testUser } });

        expect(await screen.findByRole('tab', { name: 'Namespaces' })).toHaveAttribute(
            'href',
            '/user-settings/namespaces'
        );
    });

    it('mirrors the sidebar gates: trusted publishers only when they apply', async () => {
        const { getTrustedPublishingStatus, service } = settingsServiceStub({ status: disabledStatus });
        renderWithProviders(<UserSettingsMobileNav />, { mainContext: { service, user: testUser } });

        await waitFor(() => expect(getTrustedPublishingStatus).toHaveBeenCalled());
        expect(screen.queryByText('Trusted Publishers')).not.toBeInTheDocument();
        expect(screen.getByText('Access Tokens')).toBeInTheDocument();
    });

    it('links each applicable tab to its settings route', async () => {
        const { service } = settingsServiceStub({
            status: enabledStatus
        });
        renderWithProviders(<UserSettingsMobileNav />, { mainContext: { service, user: testUser } });

        expect(await screen.findByRole('tab', { name: 'Trusted Publishers' })).toHaveAttribute(
            'href',
            '/user-settings/trusted-publishers'
        );
        expect(screen.getByRole('tab', { name: 'Profile' })).toHaveAttribute('href', '/user-settings/profile');
    });
});
