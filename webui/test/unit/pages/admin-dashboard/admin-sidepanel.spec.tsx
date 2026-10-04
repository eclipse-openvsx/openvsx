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

import { FunctionComponent } from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useNavigate } from 'react-router';
import { AdminSidepanel } from '../../../../src/pages/admin-dashboard/admin-sidepanel';
import { NavEntry } from '../../../../src/pages/admin-dashboard/nav-types';
import { renderWithProviders } from '../../support/test-providers';

const items: NavEntry[] = [
    { name: 'First', icon: <span />, children: [{ path: '/a', name: 'A Page', icon: <span /> }] },
    { name: 'Second', icon: <span />, children: [{ path: '/b', name: 'B Page', icon: <span /> }] }
];

/** The dashboard stays mounted across its own route changes, so this drives one in-place. */
const SidepanelWithNavigation: FunctionComponent = () => {
    const navigate = useNavigate();
    return (
        <>
            <button onClick={() => navigate('/b')}>Go to B</button>
            <AdminSidepanel items={items} />
        </>
    );
};

describe('AdminSidepanel', () => {
    it('expands the first group by default', () => {
        renderWithProviders(<AdminSidepanel items={items} />, { route: '/' });

        // A collapsed group's Collapse unmounts its children, so visibility is the signal.
        expect(screen.getByText('A Page')).toBeInTheDocument();
        expect(screen.queryByText('B Page')).not.toBeInTheDocument();
    });

    // A group's children unmount while collapsed, so the active page must keep its own
    // group expanded even when that isn't the first group.
    it('also expands a later group when the current page lives inside it', () => {
        renderWithProviders(<AdminSidepanel items={items} />, { route: '/b' });

        expect(screen.getByText('A Page')).toBeInTheDocument();
        expect(screen.getByText('B Page')).toBeInTheDocument();
    });

    it('expands the newly active group after an in-place navigation, without collapsing the other', async () => {
        renderWithProviders(<SidepanelWithNavigation />, { route: '/' });

        await userEvent.click(screen.getByText('Go to B'));

        expect(screen.getByText('B Page')).toBeInTheDocument();
        expect(screen.getByText('A Page')).toBeInTheDocument();
    });
});
