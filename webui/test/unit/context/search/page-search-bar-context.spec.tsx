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
import { renderWithProviders } from '../../support/test-providers';
import { createTestViewport } from '../../support/viewport';
import { NavFieldProbe, PageSearchBarProbe } from '../../support/page-search-bar';

function renderBar() {
    const viewport = createTestViewport({ initiallyInView: true });
    const view = renderWithProviders(
        <>
            <NavFieldProbe />
            <PageSearchBarProbe />
        </>,
        { viewport: viewport.observer }
    );
    return { ...view, viewport };
}

const pageField = () => screen.getByLabelText('page search');
const navFieldProbe = () => screen.getByTestId('nav-field');
const focusRequests = () => Number(navFieldProbe().dataset.focusRequests);

describe('useSearchBar', () => {
    it('registers while the bar is in view, so the nav field hides', () => {
        const { viewport } = renderBar();

        expect(navFieldProbe()).toHaveAttribute('data-page-search-bar', 'true');
        expect(pageField()).toHaveAttribute('data-registered', 'true');

        viewport.setInView(false);

        expect(navFieldProbe()).toHaveAttribute('data-page-search-bar', 'false');
        expect(pageField()).toHaveAttribute('data-registered', 'false');
    });

    it('takes focus requests (the "/" shortcut) only while registered', async () => {
        const { viewport } = renderBar();

        await userEvent.click(navFieldProbe());
        expect(pageField()).toHaveFocus();

        viewport.setInView(false);
        await userEvent.click(navFieldProbe());
        expect(pageField()).not.toHaveFocus();
    });

    it('hands focus back to the nav field when scrolled away while focused', async () => {
        const { viewport } = renderBar();
        await userEvent.click(navFieldProbe());
        const before = focusRequests();

        viewport.setInView(false);

        expect(focusRequests()).toBe(before + 1);
    });

    it('does not hand focus away when the viewport shrinks (mobile keyboard) while focused', async () => {
        const { viewport } = renderBar();
        await userEvent.click(navFieldProbe());
        const before = focusRequests();
        expect(pageField()).toHaveFocus();

        const originalHeight = window.innerHeight;
        Object.defineProperty(window, 'innerHeight', { value: originalHeight - 300, configurable: true });
        try {
            viewport.setInView(false);

            expect(focusRequests()).toBe(before);
            expect(pageField()).toHaveAttribute('data-registered', 'true');
        } finally {
            Object.defineProperty(window, 'innerHeight', { value: originalHeight, configurable: true });
        }
    });

    it('hands focus back to the nav field when unmounted while focused', async () => {
        const { rerender } = renderBar();
        await userEvent.click(navFieldProbe());
        const before = focusRequests();

        rerender(<NavFieldProbe />);

        expect(focusRequests()).toBe(before + 1);
    });

    it('does not ask for focus when it stops being the bar without having it', () => {
        const { viewport } = renderBar();
        const before = focusRequests();

        viewport.setInView(false);

        expect(focusRequests()).toBe(before);
    });
});
