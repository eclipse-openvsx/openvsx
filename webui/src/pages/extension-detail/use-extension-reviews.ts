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

import { useContext } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { Extension, NewReview, UrlString, UserData } from '../../extension-registry-types';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { extensionDetailKeys } from './use-extension-details';

const reviewsKey = (extension: Extension) => ['extension-reviews', extension.namespace, extension.name];

export const useExtensionReviews = (extension: Extension) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: reviewsKey(extension),
            queryFn: ({ signal }) => service.getExtensionReviews(signal, extension)
        })
    );
};

// A review changes the list and the rating and review count in the page header. Returned to
// `onSuccess`, so a mutation stays pending until both have been refetched.
const useRefreshReviews = (extension: Extension) => {
    const queryClient = useQueryClient();
    return () =>
        Promise.all([
            queryClient.invalidateQueries({ queryKey: reviewsKey(extension) }),
            queryClient.invalidateQueries({
                queryKey: extensionDetailKeys.all(extension.namespace, extension.name)
            })
        ]);
};

export const usePostReview = (extension: Extension) => {
    const { service } = useContext(MainContext);
    const refreshReviews = useRefreshReviews(extension);
    return useReportedQuery(
        useMutation({
            mutationFn: ({ review, postUrl }: { review: NewReview; postUrl: UrlString }) =>
                service.postReview(review, postUrl),
            onSuccess: refreshReviews
        })
    );
};

/** Revokes the current user's own review. */
export const useDeleteReview = (extension: Extension) => {
    const { service } = useContext(MainContext);
    const refreshReviews = useRefreshReviews(extension);
    return useReportedQuery(
        useMutation({
            mutationFn: (deleteUrl: UrlString) => service.deleteReview(deleteUrl),
            onSuccess: refreshReviews
        })
    );
};

/** Removes another user's review, as a moderator. */
export const useDeleteUserReview = (extension: Extension) => {
    const { service } = useContext(MainContext);
    const refreshReviews = useRefreshReviews(extension);
    return useReportedQuery(
        useMutation({
            mutationFn: (user: UserData) => service.deleteUserReview(extension, user),
            onSuccess: refreshReviews
        })
    );
};
