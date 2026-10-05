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
import { useLocation, useNavigate } from 'react-router';
import { renderWithProviders } from './support/test-providers';
import { createTestViewport, TestViewport } from './support/viewport';
import { NavFieldProbe } from './support/page-search-bar';
import { NotFound } from '../../src/not-found';

/** Shows the current URL and navigates to another missing address on demand. */
const LocationProbe = () => {
    const { pathname, search } = useLocation();
    const navigate = useNavigate();
    return (
        <>
            <span data-testid='location'>{pathname + search}</span>
            <button onClick={() => navigate('/elsewhere')}>go elsewhere</button>
        </>
    );
};

function renderNotFound(route: string, viewport?: TestViewport) {
    renderWithProviders(
        <>
            <LocationProbe />
            <NavFieldProbe />
            <NotFound />
        </>,
        { route, viewport: viewport?.observer }
    );
}

const searchField = () => screen.getByLabelText('Search extensions');
const navFieldProbe = () => screen.getByTestId('nav-field');

describe('NotFound', () => {
    it('registers as the page search bar, so the nav bar hides its own field', () => {
        renderNotFound('/missing');

        expect(navFieldProbe()).toHaveAttribute('data-page-search-bar', 'true');
    });

    it('watches the new field after moving to another missing address', async () => {
        const viewport = createTestViewport({ initiallyInView: true });
        renderNotFound('/missing', viewport);

        await userEvent.click(screen.getByText('go elsewhere'));

        expect(searchField()).toHaveValue('elsewhere');
        expect(viewport.observed()).toHaveLength(1);
        expect(viewport.observed()[0].node).toBe(searchField());
    });

    it('prefills the search field with the last path segment, decoded, without searching', () => {
        renderNotFound('/foo/caf%C3%A9');

        expect(searchField()).toHaveValue('café');
        expect(screen.getByTestId('location')).toHaveTextContent('/foo/caf%C3%A9');
    });

    it('keeps an encoded slash inside its segment', () => {
        renderNotFound('/foo/a%2Fb');

        expect(searchField()).toHaveValue('a/b');
    });

    it('keeps a segment with a malformed escape as-is', () => {
        renderNotFound('/100%');

        expect(searchField()).toHaveValue('100%');
    });

    it('searches for the prefilled term on submit', async () => {
        renderNotFound('/foo/pyhton');

        await userEvent.type(searchField(), '{Enter}');

        expect(screen.getByTestId('location')).toHaveTextContent('/search?q=pyhton');
    });
});
