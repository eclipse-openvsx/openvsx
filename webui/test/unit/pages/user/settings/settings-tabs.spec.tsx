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
import { HelmetProvider } from 'react-helmet-async';
import { Route, Routes } from 'react-router';
import { renderHookWithProviders, renderWithProviders } from '../../../support/test-providers';
import { disabledStatus, enabledStatus, testUser } from '../../../support/trusted-publishing';
import { settingsServiceStub, testCustomer } from '../../../support/user-settings';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { useSettingsTabs, UserSettingsTab } from '../../../../../src/pages/user/settings/settings-tabs';
import { UserSettings } from '../../../../../src/pages/user/user-settings';
import { PageSettings } from '../../../../../src/page-settings';

const renderTabs = (service: ExtensionRegistryService, user?: typeof testUser) =>
    renderHookWithProviders(() => useSettingsTabs(), { mainContext: { service, user } });

describe('useSettingsTabs — trusted publishing gate', () => {
    it('includes the Trusted Publishers tab when the status reports the feature enabled', async () => {
        const { service } = settingsServiceStub({ status: enabledStatus });
        const { result } = renderTabs(service, testUser);

        await waitFor(() => expect(result.current.map(tab => tab.value)).toContain('trusted-publishers'));
    });

    it('omits the tab when the status reports the feature disabled', async () => {
        const { getTrustedPublishingStatus, service } = settingsServiceStub({ status: disabledStatus });
        const { result } = renderTabs(service, testUser);

        await waitFor(() => expect(getTrustedPublishingStatus).toHaveBeenCalled());
        expect(result.current.map(tab => tab.value)).not.toContain('trusted-publishers');
        expect(result.current.map(tab => tab.value)).toContain('tokens');
    });

    it('omits the tab without a logged-in user, without querying the status', () => {
        const { getTrustedPublishingStatus, service } = settingsServiceStub({ status: enabledStatus });
        const { result } = renderTabs(service);

        expect(getTrustedPublishingStatus).not.toHaveBeenCalled();
        expect(result.current.map(tab => tab.value)).not.toContain('trusted-publishers');
    });
});

describe('useSettingsTabs — rate limiting gate', () => {
    it('includes the Rate Limiting tab only for members of a customer group', async () => {
        const { service } = settingsServiceStub({ status: disabledStatus, customers: [testCustomer()] });
        const { result } = renderTabs(service, testUser);

        await waitFor(() => expect(result.current.map(tab => tab.value)).toContain('customers'));
    });

    it('omits the Rate Limiting tab when the user belongs to no customer group', async () => {
        const { getCustomers, service } = settingsServiceStub({ status: disabledStatus });
        const { result } = renderTabs(service, testUser);

        await waitFor(() => expect(getCustomers).toHaveBeenCalled());
        expect(result.current.map(tab => tab.value)).not.toContain('customers');
    });
});

describe('useSettingsTabs — contributed tabs', () => {
    const contributed = (overrides: Partial<UserSettingsTab> = {}): UserSettingsTab => ({
        value: 'extra',
        label: 'Extra',
        icon: null,
        element: <p>extra element</p>,
        ...overrides
    });

    const contextFor = (tabs: UserSettingsTab[]) => {
        const { service } = settingsServiceStub({ status: disabledStatus });
        const pageSettings = { pageTitle: 'Test', elements: { userSettingsTabs: tabs }, urls: {} } as PageSettings;
        return { service, user: testUser, pageSettings };
    };

    const renderContributed = (tabs: UserSettingsTab[]) =>
        renderHookWithProviders(() => useSettingsTabs(), { mainContext: contextFor(tabs) });

    const renderSettings = (tabs: UserSettingsTab[], route: string) =>
        renderWithProviders(
            <HelmetProvider>
                <Routes>
                    <Route path='/user-settings/:tab' element={<UserSettings userLoading={false} />} />
                </Routes>
            </HelmetProvider>,
            { route, mainContext: contextFor(tabs) }
        );

    it('contributed tab is listed and rendered when applicable', async () => {
        const { result } = renderContributed([contributed()]);
        expect(result.current.map(tab => tab.value)).toEqual(['profile', 'tokens', 'extensions', 'extra']);

        renderSettings([contributed()], '/user-settings/extra');
        expect(await screen.findByText('extra element')).toBeInTheDocument();
    });

    it('contributed tab is hidden when useIsApplicable returns false', () => {
        const { result } = renderContributed([contributed({ useIsApplicable: () => false })]);
        expect(result.current.map(tab => tab.value)).not.toContain('extra');
    });

    it('contributed tab with a built-in value is ignored', async () => {
        const tab = contributed({ value: 'tokens', label: 'Hijacked', element: <p>hijacked element</p> });
        const { result } = renderContributed([tab]);
        expect(result.current.filter(t => t.value === 'tokens').map(t => t.label)).toEqual(['Access Tokens']);

        renderSettings([tab], '/user-settings/tokens');
        await waitFor(() => expect(screen.getAllByText('Access Tokens').length).toBeGreaterThan(0));
        expect(screen.queryByText('hijacked element')).not.toBeInTheDocument();
    });
});
