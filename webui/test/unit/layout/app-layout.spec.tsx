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
 ********************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Main } from '../../../src/main';
import { PageSettings } from '../../../src/page-settings';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';
import { renderInEntryShell } from '../support/test-providers';

const Home = () => <div>home</div>;

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountApp = (banner?: PageSettings['elements']['banner']) => {
    const service = {
        getUser: vi.fn().mockResolvedValue({ error: 'Not logged in' }),
        getRegistryVersion: vi.fn().mockResolvedValue({ version: '1.0.0' }),
        getSiteSettings: vi.fn().mockResolvedValue({
            bannerEnabled: true,
            bannerMessage: 'Configured by an admin',
            bannerSeverity: 'info',
            bannerDismissId: 'token-1'
        })
    } as unknown as ExtensionRegistryService;
    const pageSettings = {
        pageTitle: 'test',
        elements: { home: Home, banner },
        urls: { extensionDefaultIcon: '', namespaceAccessInfo: '' }
    } as PageSettings;

    renderInEntryShell(<Main service={service} pageSettings={pageSettings} loginProviders={{}} />);
    return service;
};

describe('AppLayout banner', () => {
    it("uses the admin-configured banner when the page settings don't bring one", async () => {
        mountApp();

        expect(await screen.findByText('Configured by an admin')).toBeVisible();
    });

    it('leaves a page-settings banner in place and never fetches the site settings', async () => {
        const service = mountApp({ content: () => <span>From page settings</span> });

        expect(await screen.findByText('From page settings')).toBeInTheDocument();
        await waitFor(() => expect(service.getUser).toHaveBeenCalled());
        expect(service.getSiteSettings).not.toHaveBeenCalled();
    });
});
