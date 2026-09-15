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

import { FunctionComponent } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SxProps, Theme } from '@mui/material/styles';
import { menuItemLabels } from '../support/menu-queries';
import { renderWithProviders } from '../support/test-providers';
import { MobileUserAvatar } from '../../../src/default/menu-content';
import { PageSettings, UserMenuContentProps } from '../../../src/page-settings';
import { UserData } from '../../../src/extension-registry-types';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';

const admin: UserData = { loginName: 'testuser', tokensUrl: '', createTokenUrl: '', role: 'admin' };

// The logout entry wraps a real form that reads both of these while rendering.
const service = {
    getLogoutUrl: () => '/logout',
    getCsrfToken: vi.fn().mockResolvedValue({ value: 'csrf' })
} as unknown as ExtensionRegistryService;

// Records the sx the host hands the icon, which is how we assert the mobile menu — not the
// consumer — owns the entry's presentation.
let iconSx: SxProps<Theme> | undefined;
const ProbeIcon: FunctionComponent<{ sx?: SxProps<Theme> }> = ({ sx }) => {
    iconSx = sx;
    return <span />;
};

const ConsumerEntry: FunctionComponent<UserMenuContentProps> = ({ MenuEntry }) => (
    <MenuEntry to='/analytics' icon={ProbeIcon}>
        Analytics
    </MenuEntry>
);

function renderAvatar(userMenuContent?: PageSettings['elements']['userMenuContent']) {
    return renderWithProviders(<MobileUserAvatar />, {
        mainContext: { service, user: admin, pageSettings: { elements: { userMenuContent } } as PageSettings }
    });
}

/** The avatar itself is a menu entry; opening its menu adds the rest. */
async function openMenu(): Promise<void> {
    await userEvent.click(screen.getByText(admin.loginName));
}

describe('MobileUserAvatar', () => {
    it('renders the pageSettings userMenuContent entries above the admin entry', async () => {
        renderAvatar(ConsumerEntry);
        await openMenu();

        expect(menuItemLabels()).toEqual([
            admin.loginName,
            admin.loginName,
            'Settings',
            'Analytics',
            'Admin Dashboard',
            'Log Out'
        ]);
    });

    it('closes the menu when a contributed entry is clicked', async () => {
        renderAvatar(ConsumerEntry);
        await openMenu();

        await userEvent.click(screen.getByText('Analytics'));

        await waitFor(() => expect(menuItemLabels()).toEqual([admin.loginName]));
    });

    it('styles a contributed entry like its own', async () => {
        iconSx = undefined;
        renderAvatar(ConsumerEntry);
        await openMenu();

        // The mobile menu's own icon styling, which differs from the desktop menu's.
        expect(iconSx).toEqual({ mr: 1, width: '16px', height: '16px' });
    });

    it('renders only the built-in entries when no userMenuContent is configured', async () => {
        renderAvatar();
        await openMenu();

        expect(menuItemLabels()).toEqual([admin.loginName, admin.loginName, 'Settings', 'Admin Dashboard', 'Log Out']);
    });
});
