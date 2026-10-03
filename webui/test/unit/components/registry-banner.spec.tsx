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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistryBanner } from '../../../src/components/registry-banner';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';
import { Settings } from '../../../src/extension-registry-types';
import { renderWithProviders } from '../support/test-providers';

const DISMISSED_KEY = 'openvsx-banner-dismissed';

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountBanner = (settings: Settings) => {
    const getSiteSettings = vi.fn().mockResolvedValue(settings);
    renderWithProviders(<RegistryBanner />, {
        mainContext: { service: { getSiteSettings } as unknown as ExtensionRegistryService }
    });
    return getSiteSettings;
};

describe('RegistryBanner', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it('renders the configured message as markdown', async () => {
        mountBanner({
            'banner-enabled': true,
            'banner-message': 'Scheduled **maintenance** tonight',
            'banner-severity': 'warning',
            'banner-dismiss-id': 'token-1'
        });

        const emphasised = await screen.findByText('maintenance');
        expect(emphasised.tagName).toBe('STRONG');
        expect(emphasised).toBeVisible();
    });

    it('stays collapsed when the registry serves no banner keys', async () => {
        const getSiteSettings = mountBanner({});

        await waitFor(() => expect(getSiteSettings).toHaveBeenCalled());
        expect(screen.getByLabelText('Dismiss')).not.toBeVisible();
    });

    it("remembers a dismissal by the banner's token", async () => {
        mountBanner({
            'banner-enabled': true,
            'banner-message': 'Maintenance tonight',
            'banner-severity': 'info',
            'banner-dismiss-id': 'token-1'
        });

        await screen.findByText('Maintenance tonight');
        await userEvent.click(screen.getByLabelText('Dismiss'));

        await waitFor(() => expect(screen.getByText('Maintenance tonight')).not.toBeVisible());
        expect(localStorage.getItem(DISMISSED_KEY)).toBe(JSON.stringify('token-1'));
    });

    it('stays dismissed when the message is corrected under the same token', async () => {
        localStorage.setItem(DISMISSED_KEY, JSON.stringify('token-1'));
        const getSiteSettings = mountBanner({
            'banner-enabled': true,
            'banner-message': 'Maintenance tonight',
            'banner-severity': 'info',
            'banner-dismiss-id': 'token-1'
        });

        await waitFor(() => expect(getSiteSettings).toHaveBeenCalled());
        await waitFor(() => expect(screen.getByText('Maintenance tonight')).not.toBeVisible());
    });

    it('comes back once an admin rotates the token', async () => {
        localStorage.setItem(DISMISSED_KEY, JSON.stringify('token-1'));
        mountBanner({
            'banner-enabled': true,
            'banner-message': 'Maintenance tonight',
            'banner-severity': 'info',
            'banner-dismiss-id': 'token-2'
        });

        expect(await screen.findByText('Maintenance tonight')).toBeVisible();
    });
});
