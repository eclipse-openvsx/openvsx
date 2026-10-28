/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { Fragment, FunctionComponent, ReactNode, useContext, useState } from 'react';
import {
    Box,
    Typography,
    Divider,
    Link,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    DialogActions,
    Button
} from '@mui/material';
import { MainContext } from '../../context';
import { toLocalTime } from '../../utils';
import { ExtensionReview, Extension, ExtensionReviewList, isEqualUser, UserData } from '../../extension-registry-types';
import { TextDivider } from '../../components/text-divider';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Timestamp } from '../../components/timestamp';
import { ExtensionRatingStars } from './extension-rating-stars';
import { ExtensionReviewDialog } from './extension-review-dialog';
import { LoginComponent } from '../../default/login';
import { hasPermission } from '../../permissions';
import { useDeleteReview, useDeleteUserReview, useExtensionReviews } from './use-extension-reviews';

export const ExtensionDetailReviews: FunctionComponent<ExtensionDetailReviewsProps> = props => {
    const [removeDialogOpen, setRemoveDialogOpen] = useState(false);
    const [removeCommentFromUser, setRemoveCommentFromUser] = useState<UserData | undefined>(undefined);
    const context = useContext(MainContext);
    const { data: reviewList, isFetching: reviewsLoading } = useExtensionReviews(props.extension);
    const { mutate: deleteReview, isPending: revoking } = useDeleteReview(props.extension);
    const {
        mutate: deleteUserReview,
        isPending: removingReview,
        variables: removingReviewOf
    } = useDeleteUserReview(props.extension);

    const handleRevokeButton = () => deleteReview(reviewList!.deleteUrl);

    const renderButton = (): ReactNode => {
        // An anonymous visitor gets the login in place of the review button. It doesn't depend on
        // reviewList, so it comes before the check the signed-in branches below need.
        if (!context.user) {
            // userLoading: undefined user is not yet logged out, and prompting one who is signed in
            // would send them through OAuth again
            if (context.userLoading || !context.loginProviders) {
                return '';
            }
            return (
                <Box>
                    <LoginComponent
                        loginProviders={context.loginProviders}
                        renderButton={(href, onClick) => (
                            <Button variant='contained' color='secondary' href={href} onClick={onClick}>
                                Log in to Review
                            </Button>
                        )}
                    />
                </Box>
            );
        }
        if (!reviewList) {
            return '';
        }
        const existingReview = reviewList.reviews.find(r => isEqualUser(r.user, context.user as UserData));
        if (existingReview) {
            const localTime = toLocalTime(existingReview.timestamp);
            return (
                <ButtonWithProgress
                    working={revoking}
                    onClick={handleRevokeButton}
                    title={`Revoke review written by ${context.user.loginName} on ${localTime}`}>
                    Revoke my Review
                </ButtonWithProgress>
            );
        } else {
            return (
                <Box>
                    <ExtensionReviewDialog extension={props.extension} reviewPostUrl={reviewList.postUrl} />
                </Box>
            );
        }
    };

    const handleAdminRemoveReviewButton = () => {
        if (removeCommentFromUser === undefined) {
            return;
        }
        deleteUserReview(removeCommentFromUser, { onSuccess: () => setRemoveDialogOpen(false) });
    };

    const renderAdminRemoveButton = (r: ExtensionReview): ReactNode => {
        return (
            <Button
                variant='contained'
                color='error'
                sx={{ ml: 1 }}
                onClick={() => {
                    setRemoveCommentFromUser(r.user);
                    setRemoveDialogOpen(true);
                }}>
                Remove review
            </Button>
        );
    };

    const renderAdminRemoveDialog = () => {
        return (
            <Dialog open={removeDialogOpen} onClose={() => setRemoveDialogOpen(false)}>
                <DialogTitle>Remove Review</DialogTitle>
                <DialogContent>
                    <DialogContentText component='div'>
                        <Typography>
                            Confirm removal of review comment from <code>{removeCommentFromUser?.loginName}</code>?
                        </Typography>
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant='contained' color='primary' onClick={() => setRemoveDialogOpen(false)}>
                        Cancel
                    </Button>
                    <ButtonWithProgress
                        autoFocus
                        color='error'
                        sx={{ ml: 1 }}
                        working={removingReview && removingReviewOf?.loginName === removeCommentFromUser?.loginName}
                        onClick={() => handleAdminRemoveReviewButton()}>
                        Remove review
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        );
    };

    const renderReviewList = (list?: ExtensionReviewList): ReactNode => {
        if (!list) {
            return '';
        }
        if (list.reviews.length === 0) {
            return (
                <Box mt={3}>
                    <Typography>Be the first to review this extension</Typography>
                </Box>
            );
        }
        return list.reviews.map(renderReview.bind(this));
    };

    const renderReview = (r: ExtensionReview): ReactNode => {
        return (
            <Fragment key={r.user.loginName + r.timestamp}>
                <Box display='flex' justifyContent='space-between'>
                    <Box my={2}>
                        <Box display='flex'>
                            {r.timestamp ? (
                                <>
                                    <Typography variant='body2'>
                                        <Timestamp value={r.timestamp} />
                                    </Typography>
                                    <TextDivider />
                                </>
                            ) : null}
                            <Typography variant='body2'>
                                {r.user.homepage ? (
                                    <Link href={r.user.homepage} color='text.primary' underline='hover'>
                                        {r.user.loginName}
                                    </Link>
                                ) : (
                                    r.user.loginName
                                )}
                            </Typography>
                        </Box>
                        <Box display='flex' alignItems='center'>
                            <ExtensionRatingStars number={r.rating} />
                        </Box>
                        <Box overflow='auto'>
                            <Typography variant='body1' sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {r.comment}
                            </Typography>
                        </Box>
                    </Box>
                    {hasPermission(context.user, 'manage_extensions') ? (
                        <Box sx={{ mb: 2, minWidth: 160 }} display='flex' alignItems='end'>
                            {renderAdminRemoveButton(r)}
                        </Box>
                    ) : null}
                </Box>
                <Divider />
            </Fragment>
        );
    };

    return (
        <>
            <Box
                sx={{
                    mb: 2,
                    display: 'flex',
                    justifyContent: 'space-between',
                    ['@media(max-width: 360px)']: {
                        flexDirection: 'column',
                        '& > div:first-of-type': {
                            marginBottom: '1rem'
                        },
                        '& button': {
                            maxWidth: '12rem'
                        }
                    }
                }}>
                <Box>
                    <Typography variant='h5'>User Reviews</Typography>
                </Box>
                {renderButton()}
            </Box>
            <Divider />
            <Box>
                <DelayedLoadIndicator loading={reviewsLoading} />
                {renderReviewList(reviewList)}
                {hasPermission(context.user, 'manage_extensions') && renderAdminRemoveDialog()}
            </Box>
        </>
    );
};

export interface ExtensionDetailReviewsProps {
    extension: Extension;
}
