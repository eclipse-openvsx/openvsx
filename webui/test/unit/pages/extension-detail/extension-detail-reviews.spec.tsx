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

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { testUser } from '../../support/trusted-publishing';
import { ExtensionDetailReviews } from '../../../../src/pages/extension-detail/extension-detail-reviews';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, ExtensionReviewList, UserData } from '../../../../src/extension-registry-types';

const extension = { namespace: 'foo', name: 'bar', displayName: 'Bar' } as Extension;

const emptyReviews: ExtensionReviewList = { postUrl: '/review', deleteUrl: '/review/delete', reviews: [] };

function renderReviews(options?: { user?: UserData; loginProviders?: Record<string, string> }) {
    const getExtensionReviews = vi.fn().mockResolvedValue(emptyReviews);
    renderWithProviders(<ExtensionDetailReviews extension={extension} reviewsDidUpdate={() => {}} />, {
        mainContext: {
            service: { getExtensionReviews } as unknown as ExtensionRegistryService,
            user: options?.user,
            loginProviders: options?.loginProviders
        }
    });
    return { getExtensionReviews };
}

describe('ExtensionDetailReviews', () => {
    it('prompts a signed-out visitor to log in, linking straight to the only provider', async () => {
        renderReviews({ loginProviders: { github: 'https://open-vsx.org/oauth2/authorization/github' } });

        const login = await screen.findByRole('link', { name: 'Log in to Review' });
        expect(login).toHaveAttribute('href', 'https://open-vsx.org/oauth2/authorization/github');
    });

    it('lets a signed-out visitor pick a provider when the registry has several', async () => {
        renderReviews({
            loginProviders: {
                github: 'https://open-vsx.org/oauth2/authorization/github',
                eclipse: 'https://open-vsx.org/oauth2/authorization/eclipse'
            }
        });

        await userEvent.click(await screen.findByRole('button', { name: 'Log in to Review' }));

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Log In');
        expect(screen.getByRole('link', { name: 'github' })).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/github'
        );
        expect(screen.getByRole('link', { name: 'eclipse' })).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/eclipse'
        );
    });

    it('offers no login prompt on a registry with no login providers configured', async () => {
        const { getExtensionReviews } = renderReviews();

        await waitFor(() => expect(getExtensionReviews).toHaveBeenCalled());
        expect(screen.queryByText(/Log in to Review/)).not.toBeInTheDocument();
    });

    it('offers the review button, not the login prompt, once signed in', async () => {
        renderReviews({
            user: testUser,
            loginProviders: { github: 'https://open-vsx.org/oauth2/authorization/github' }
        });

        expect(await screen.findByRole('button', { name: 'Write a Review' })).toBeInTheDocument();
        expect(screen.queryByText(/Log in to Review/)).not.toBeInTheDocument();
    });
});
