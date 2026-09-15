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
 *****************************************************************************/

import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { AdminDashboard } from '../../../../src/pages/admin-dashboard/admin-dashboard';
import { AdminPage } from '../../../../src/pages/admin-dashboard/nav-types';
import { MainContext } from '../../../../src/context';
import { PageSettings } from '../../../../src/page-settings';
import { UserData } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const admin = { loginName: 'root', role: 'admin' } as UserData;

const agentsPage: AdminPage = {
    path: 'analytics/agents',
    name: 'Agents',
    icon: <span />,
    description: 'Link customers to analytics user agents',
    element: <div>contributed page body</div>
};

/**
 * `AdminDashboard` owns a nested `Routes`, and in the app it is mounted under
 * `/admin-dashboard/*` so those paths resolve relative to that match. Rendering it
 * directly means the same route table resolves from the root instead, so a route here
 * is the contributed `path` without the dashboard prefix.
 */
function renderDashboard(adminPages: AdminPage[] | undefined, route = '/') {
    const pageSettings = { elements: { adminPages } } as PageSettings;
    const mainContext: Partial<MainContext> = { user: admin, pageSettings };
    return renderWithProviders(<AdminDashboard userLoading={false} />, { route, mainContext });
}

describe('AdminDashboard contributed pages', () => {
    it('shows a contributed page as an overview card with its description', () => {
        renderDashboard([agentsPage]);

        expect(screen.getByText('Link customers to analytics user agents')).toBeInTheDocument();
        // Once in the side panel, once as the overview card.
        expect(screen.getAllByText('Agents')).toHaveLength(2);
    });

    it('renders a contributed page at its own route', () => {
        renderDashboard([agentsPage], '/analytics/agents');

        expect(screen.getByText('contributed page body')).toBeInTheDocument();
        // The overview fallback must not also match.
        expect(screen.queryByText('Welcome to the Admin Dashboard')).not.toBeInTheDocument();
    });

    it('merges a category into the built-in group of the same name instead of adding a second one', () => {
        const { unmount } = renderDashboard(undefined);
        const withoutContribution = screen.getAllByText('Rate Limiting').length;
        unmount();

        renderDashboard([{ ...agentsPage, category: { name: 'Rate Limiting', icon: <span /> } }]);

        expect(screen.getAllByText('Rate Limiting')).toHaveLength(withoutContribution);
        expect(screen.getByText('Link customers to analytics user agents')).toBeInTheDocument();
    });

    it('ignores a contributed page that would shadow a built-in one', () => {
        renderDashboard([{ ...agentsPage, path: 'customers', name: 'Not Customers' }]);

        expect(screen.queryByText('Not Customers')).not.toBeInTheDocument();
        expect(screen.queryByText('Link customers to analytics user agents')).not.toBeInTheDocument();
        // The built-in entry is untouched.
        expect(screen.getAllByText('Customers').length).toBeGreaterThan(0);
    });

    // adminPages is consumer-provided, and a leading slash used to leave the shadowing check looking
    // at an empty first segment, so this slipped past it and sat in the nav beside the built-in page.
    it('ignores a shadowing page however its path is written', () => {
        renderDashboard([{ ...agentsPage, path: '/customers/', name: 'Not Customers' }]);

        expect(screen.queryByText('Not Customers')).not.toBeInTheDocument();
        expect(screen.getAllByText('Customers').length).toBeGreaterThan(0);
    });

    it('ignores a contributed page with an empty path', () => {
        renderDashboard([{ ...agentsPage, path: '/', name: 'Rootless' }]);

        expect(screen.queryByText('Rootless')).not.toBeInTheDocument();
    });
});
