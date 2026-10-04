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
import { AdminSidepanel } from '../../../../src/pages/admin-dashboard/admin-sidepanel';
import { NavEntry } from '../../../../src/pages/admin-dashboard/nav-types';
import { renderWithProviders } from '../../support/test-providers';

const items: NavEntry[] = [
    { name: 'First', icon: <span />, children: [{ path: '/a', name: 'A Page', icon: <span /> }] },
    { name: 'Second', icon: <span />, children: [{ path: '/b', name: 'B Page', icon: <span /> }] }
];

describe('AdminSidepanel', () => {
    it('expands the first group by default', () => {
        renderWithProviders(<AdminSidepanel items={items} />, { route: '/' });

        // A collapsed group's Collapse unmounts its children, so visibility is the signal.
        expect(screen.getByText('A Page')).toBeInTheDocument();
        expect(screen.queryByText('B Page')).not.toBeInTheDocument();
    });

    // Deep-linking into a page outside the first group used to leave its side panel entry
    // unmounted inside a collapsed group, hiding the active-page indication the old flat
    // navigation always showed.
    it('also expands a later group when the current page lives inside it', () => {
        renderWithProviders(<AdminSidepanel items={items} />, { route: '/b' });

        expect(screen.getByText('A Page')).toBeInTheDocument();
        expect(screen.getByText('B Page')).toBeInTheDocument();
    });
});
