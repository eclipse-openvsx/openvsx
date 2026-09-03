/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { Fragment, FunctionComponent, ReactNode, useContext, useState, useEffect, useRef } from 'react';
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
import {
    ExtensionReview,
    Extension,
    ExtensionReviewList,
    isEqualUser,
    isError,
    UserData
} from '../../extension-registry-types';
import { TextDivider } from '../../components/text-divider';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Timestamp } from '../../components/timestamp';
import { ExtensionRatingStars } from './extension-rating-stars';
import { ExtensionReviewDialog } from './extension-review-dialog';
import { LoginComponent } from '../../default/login';

export const ExtensionDetailReviews: FunctionComponent<ExtensionDetailReviewsProps> = props => {
    const [reviewList, setReviewList] = useState<ExtensionReviewList>();
    const [loading, setLoading] = useState<boolean>(true);
    const [revoked, setRevoked] = useState<boolean>(false);
    const [removeDialogOpen, setRemoveDialogOpen] = useState(false);
    const [removeCommentFromUser, setRemoveCommentFromUser] = useState<UserData | undefined>(undefined);
    const [removeReviewSet, setRemoveReviewSet] = useState(new Set<string>());
    const context = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    const addRemoveReviewRequest = (loginName: string) => {
        const newSet = new Set<string>(removeReviewSet);
        newSet.add(loginName);
        setRemoveReviewSet(newSet);
    };

    const deleteRemoveReviewRequest = (loginName: string) => {
        const newSet = new Set<string>(removeReviewSet);
        newSet.delete(loginName);
        setRemoveReviewSet(newSet);
    };

    useEffect(() => {
        updateReviews();
        return () => abortController.current.abort();
    }, []);

    const updateReviews = async () => {
        try {
            const reviewList = await context.service.getExtensionReviews(abortController.current, props.extension);
            setReviewList(reviewList);
        } catch (err) {
            context.handleError(err);
        }

        setLoading(false);
        setRevoked(false);
    };

    const saveCompleted = () => {
        setLoading(true);
        updateReviews();
        props.reviewsDidUpdate();
    };

    const handleRevokeButton = async () => {
        setRevoked(true);
        try {
            const result = await context.service.deleteReview(abortController.current, reviewList!.deleteUrl);
            if (isError(result)) {
                throw result;
            }
            saveCompleted();
        } catch (err) {
            context.handleError(err);
        }
    };

    const renderButton = (): ReactNode => {
        // Nothing here tells an anonymous visitor that reviewing needs an account, so offer the
        // login in place of the review button rather than rendering an empty slot. Doesn't wait
        // on reviewList (unlike the logged-in cases below, which need to know whether this user
        // already reviewed) - there's nothing about the prompt that depends on it. Suppressed
        // entirely on a registry with no login providers configured, where there's nothing to
        // offer; same guard the header and publish page use.
        if (!context.user) {
            if (!context.loginProviders) {
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
                    working={revoked}
                    onClick={handleRevokeButton}
                    title={`Revoke review written by ${context.user.loginName} on ${localTime}`}>
                    Revoke my Review
                </ButtonWithProgress>
            );
        } else {
            return (
                <Box>
                    <ExtensionReviewDialog
                        saveCompleted={saveCompleted}
                        extension={props.extension}
                        reviewPostUrl={reviewList.postUrl}
                    />
                </Box>
            );
        }
    };

    const handleAdminRemoveReviewButton = async () => {
        if (removeCommentFromUser === undefined) {
            return;
        }
        addRemoveReviewRequest(removeCommentFromUser.loginName);
        try {
            const result = await context.service.deleteUserReview(
                abortController.current,
                props.extension,
                removeCommentFromUser
            );
            if (isError(result)) {
                throw result;
            }
            saveCompleted();
            setRemoveDialogOpen(false);
        } catch (err) {
            context.handleError(err);
        } finally {
            deleteRemoveReviewRequest(removeCommentFromUser.loginName);
        }
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
                        working={removeReviewSet.has(removeCommentFromUser?.loginName ?? '')}
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
                    {context.user?.role === 'admin' ? (
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
                <DelayedLoadIndicator loading={loading} />
                {renderReviewList(reviewList)}
                {context.user?.role === 'admin' && renderAdminRemoveDialog()}
            </Box>
        </>
    );
};

export interface ExtensionDetailReviewsProps {
    extension: Extension;
    reviewsDidUpdate: () => void;
}
