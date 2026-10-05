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
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../support/test-providers';
import { createTestViewport } from '../support/viewport';
import { NavSearchField } from '../../../src/layout/nav-search-field';
import { PageSearchBarProbe } from '../support/page-search-bar';

describe('NavSearchField', () => {
    it('hands its focus to a page search bar scrolling into view', async () => {
        const viewport = createTestViewport();
        renderWithProviders(
            <>
                <NavSearchField />
                <PageSearchBarProbe />
            </>,
            { route: '/extension/foo/bar', viewport: viewport.observer }
        );
        await userEvent.click(screen.getByPlaceholderText('search extensions…'));

        viewport.setInView(true);

        expect(screen.getByLabelText('page search')).toHaveFocus();
    });
});
