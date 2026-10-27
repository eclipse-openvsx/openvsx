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
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createTestQueryClient, renderWithProviders } from '../../support/test-providers';
import { testUser } from '../../support/trusted-publishing';
import { ExtensionDetailReviews } from '../../../../src/pages/extension-detail/extension-detail-reviews';
import { extensionDetailKeys } from '../../../../src/pages/extension-detail/use-extension-details';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, ExtensionReviewList, UserData } from '../../../../src/extension-registry-types';

const extension = { namespace: 'foo', name: 'bar', displayName: 'Bar' } as Extension;

const emptyReviews: ExtensionReviewList = { postUrl: '/review', deleteUrl: '/review/delete', reviews: [] };

const oneReview: ExtensionReviewList = {
    ...emptyReviews,
    reviews: [{ user: { loginName: 'reviewer' } as UserData, rating: 4, comment: 'fine', timestamp: '' }]
};

const ownReview: ExtensionReviewList = {
    ...emptyReviews,
    reviews: [{ user: testUser, rating: 5, comment: 'mine', timestamp: '' }]
};

// where the IDE's star-rating link drops a visitor, and where a login from here must return them
const reviewsRoute = '/extension/foo/bar/reviews';

function renderReviews(options?: {
    user?: UserData;
    loginProviders?: Record<string, string>;
    userLoading?: boolean;
    reviews?: ExtensionReviewList;
    // what the list holds once a review was posted or removed
    reviewsAfterChange?: ExtensionReviewList;
}) {
    const getExtensionReviews = vi.fn().mockResolvedValueOnce(options?.reviews ?? emptyReviews);
    getExtensionReviews.mockResolvedValue(options?.reviewsAfterChange ?? options?.reviews ?? emptyReviews);
    const postReview = vi.fn().mockResolvedValue({ success: 'Added review' });
    const deleteReview = vi.fn().mockResolvedValue({ success: 'Deleted review' });
    const deleteUserReview = vi.fn().mockResolvedValue({ success: 'Deleted review' });
    const handleError = vi.fn();
    // A cached header for the extension, to see whether a review change marks it for refetching.
    const queryClient = createTestQueryClient();
    const detailKey = extensionDetailKeys.detail('foo', 'bar');
    queryClient.setQueryData(detailKey, extension);
    renderWithProviders(<ExtensionDetailReviews extension={extension} />, {
        route: reviewsRoute,
        queryClient,
        mainContext: {
            service: {
                getExtensionReviews,
                postReview,
                deleteReview,
                deleteUserReview
            } as unknown as ExtensionRegistryService,
            handleError,
            user: options?.user,
            loginProviders: options?.loginProviders,
            userLoading: options?.userLoading ?? false
        }
    });
    const headerInvalidated = () => queryClient.getQueryState(detailKey)?.isInvalidated;
    return { getExtensionReviews, postReview, deleteReview, deleteUserReview, handleError, headerInvalidated };
}

describe('ExtensionDetailReviews', () => {
    it('prompts a signed-out visitor to log in, linking straight to the only provider', async () => {
        renderReviews({ loginProviders: { github: 'https://open-vsx.org/oauth2/authorization/github' } });

        const login = await screen.findByRole('link', { name: 'Log in to Review' });
        expect(login).toHaveAttribute(
            'href',
            'https://open-vsx.org/oauth2/authorization/github?redirect=%2Fextension%2Ffoo%2Fbar%2Freviews'
        );
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
            `https://open-vsx.org/oauth2/authorization/github?redirect=%2Fextension%2Ffoo%2Fbar%2Freviews`
        );
        expect(screen.getByRole('link', { name: 'eclipse' })).toHaveAttribute(
            'href',
            `https://open-vsx.org/oauth2/authorization/eclipse?redirect=%2Fextension%2Ffoo%2Fbar%2Freviews`
        );
    });

    it('waits for the user fetch before deciding a visitor is signed out', async () => {
        const { getExtensionReviews } = renderReviews({
            userLoading: true,
            loginProviders: { github: 'https://open-vsx.org/oauth2/authorization/github' }
        });

        await waitFor(() => expect(getExtensionReviews).toHaveBeenCalled());
        expect(screen.queryByText(/Log in to Review/)).not.toBeInTheDocument();
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

    // Moderation follows manage_extensions rather than the admin role, so a plain signed-in user
    // must not be offered a control the server would refuse.
    it('offers no remove control to a user without manage_extensions', async () => {
        renderReviews({ user: testUser, reviews: oneReview });

        expect(await screen.findByText('fine')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove review' })).not.toBeInTheDocument();
    });

    it('offers the remove control to a user holding manage_extensions', async () => {
        renderReviews({ user: { ...testUser, permissions: ['manage_extensions'] }, reviews: oneReview });

        expect(await screen.findByRole('button', { name: 'Remove review' })).toBeInTheDocument();
    });

    it('posts a review and shows it in the refreshed list, refreshing the rating in the header', async () => {
        const user = userEvent.setup();
        const { postReview, headerInvalidated } = renderReviews({ user: testUser, reviewsAfterChange: ownReview });

        await user.click(await screen.findByRole('button', { name: 'Write a Review' }));
        await user.type(screen.getByLabelText('Your Review...'), 'great');
        await user.click(screen.getByRole('button', { name: 'Post Review' }));

        expect(await screen.findByRole('button', { name: 'Revoke my Review' })).toBeInTheDocument();
        expect(screen.getByText('mine')).toBeInTheDocument();
        expect(postReview).toHaveBeenCalledWith({ rating: 1, comment: 'great' }, '/review');
        expect(headerInvalidated()).toBe(true);
    });

    it('keeps the review dialog open and reports the error when posting fails', async () => {
        const user = userEvent.setup();
        const failure = { error: 'You must not submit more than one review for an extension.' };
        const { postReview, handleError } = renderReviews({ user: testUser });
        postReview.mockRejectedValue(failure);

        await user.click(await screen.findByRole('button', { name: 'Write a Review' }));
        await user.click(screen.getByRole('button', { name: 'Post Review' }));

        await waitFor(() => expect(handleError).toHaveBeenCalledWith(failure, { title: 'Couldn’t post your review' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('Bar Review');
    });

    it('revokes the own review and offers to write one again once the list is refreshed', async () => {
        const user = userEvent.setup();
        const { deleteReview, headerInvalidated } = renderReviews({
            user: testUser,
            reviews: ownReview,
            reviewsAfterChange: emptyReviews
        });

        await user.click(await screen.findByRole('button', { name: 'Revoke my Review' }));

        expect(await screen.findByRole('button', { name: 'Write a Review' })).toBeInTheDocument();
        expect(deleteReview).toHaveBeenCalledWith('/review/delete');
        expect(headerInvalidated()).toBe(true);
    });

    it("removes another user's review as a moderator and closes the confirmation", async () => {
        const user = userEvent.setup();
        const { deleteUserReview } = renderReviews({
            user: { ...testUser, permissions: ['manage_extensions'] },
            reviews: oneReview,
            reviewsAfterChange: emptyReviews
        });

        await user.click(await screen.findByRole('button', { name: 'Remove review' }));
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('button', { name: 'Remove review' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(screen.getByText('Be the first to review this extension')).toBeInTheDocument();
        expect(deleteUserReview).toHaveBeenCalledWith(extension, oneReview.reviews[0].user);
    });
});
