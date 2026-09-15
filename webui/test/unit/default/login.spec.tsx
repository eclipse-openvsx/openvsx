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
import { LoginComponent } from '../../../src/default/login';

const github = 'https://open-vsx.org/oauth2/authorization/github';
const eclipse = 'https://open-vsx.org/oauth2/authorization/eclipse';

function renderLogin(loginProviders: Record<string, string>, route?: string) {
    renderWithProviders(
        <LoginComponent
            loginProviders={loginProviders}
            renderButton={(href, onClick) => (
                <a href={href} onClick={onClick}>
                    Log In
                </a>
            )}
        />,
        { route }
    );
}

describe('LoginComponent', () => {
    it('asks to be returned to the page the login started from', () => {
        renderLogin({ github }, '/extension/foo/bar/reviews');

        expect(screen.getByRole('link', { name: 'Log In' })).toHaveAttribute(
            'href',
            `${github}?redirect=%2Fextension%2Ffoo%2Fbar%2Freviews`
        );
    });

    it('carries the query and the fragment of that page along', () => {
        renderLogin({ github }, '/search?query=java#results');

        expect(screen.getByRole('link', { name: 'Log In' })).toHaveAttribute(
            'href',
            `${github}?redirect=%2Fsearch%3Fquery%3Djava%23results`
        );
    });

    it('asks for no return from the front page, which is where a login lands anyway', () => {
        renderLogin({ github }, '/');

        expect(screen.getByRole('link', { name: 'Log In' })).toHaveAttribute('href', github);
    });

    it('asks the same of whichever provider is picked in the dialog', async () => {
        renderLogin({ github, eclipse }, '/extension/foo/bar');

        // no href on this one: several providers make it the button that opens the picker
        await userEvent.click(screen.getByText('Log In'));

        expect(screen.getByRole('link', { name: 'github' })).toHaveAttribute(
            'href',
            `${github}?redirect=%2Fextension%2Ffoo%2Fbar`
        );
        expect(screen.getByRole('link', { name: 'eclipse' })).toHaveAttribute(
            'href',
            `${eclipse}?redirect=%2Fextension%2Ffoo%2Fbar`
        );
    });
});
