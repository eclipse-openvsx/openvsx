/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, FunctionComponent, useContext, useState } from 'react';
import {
    Box,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    TextField,
    DialogActions
} from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Extension, StarRating } from '../../extension-registry-types';
import { ExtensionRatingStarSetter } from './extension-rating-star-setter';
import { MainContext } from '../../context';
import { usePostReview } from './use-extension-reviews';

const REVIEW_COMMENT_SIZE = 2048;

export const ExtensionReviewDialog: FunctionComponent<ExtensionReviewDialogProps> = props => {
    const [open, setOpen] = useState<boolean>(false);
    const [rating, setRating] = useState<StarRating>(1);
    const [comment, setComment] = useState<string>('');
    const [commentError, setCommentError] = useState<string>();
    const context = useContext(MainContext);
    const { mutate: postReview, isPending: posting } = usePostReview(props.extension);

    const handleOpenButton = () => {
        if (context.user) {
            setOpen(true);
        }
    };

    const handleCancel = () => setOpen(false);

    const handlePost = () =>
        postReview(
            { review: { rating, comment }, postUrl: props.reviewPostUrl },
            {
                onSuccess: () => {
                    setOpen(false);
                    setComment('');
                }
            }
        );

    const handleCommentChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
        const comment = event.target.value;
        let commentError: string | undefined;
        if (comment.length > REVIEW_COMMENT_SIZE) {
            commentError = `The review comment must not be longer than ${REVIEW_COMMENT_SIZE} characters.`;
        }

        setComment(comment);
        setCommentError(commentError);
    };

    if (!context.user) {
        return null;
    }
    return (
        <>
            {!posting && (
                <Button variant='contained' color='secondary' onClick={handleOpenButton}>
                    Write a Review
                </Button>
            )}
            <Dialog open={open} onClose={handleCancel}>
                <DialogTitle>{props.extension.displayName ?? props.extension.name} Review</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Your review will be posted publicly as {context.user.loginName}
                    </DialogContentText>
                    <Box
                        component='div'
                        sx={{
                            width: { xs: '140%', sm: '100%', md: '100%', lg: '100%', xl: '100%' },
                            transform: {
                                xs: 'scale(.7) translateX(-23%)',
                                sm: 'none',
                                md: 'none',
                                lg: 'none',
                                xl: 'none'
                            }
                        }}>
                        <ExtensionRatingStarSetter
                            handleRatingChange={(rating: number) => setRating(rating as StarRating)}
                        />
                    </Box>
                    <TextField
                        margin='dense'
                        label='Your Review...'
                        fullWidth
                        multiline
                        variant='outlined'
                        rows={4}
                        error={Boolean(commentError)}
                        helperText={commentError}
                        onChange={handleCommentChange}
                    />
                </DialogContent>
                <DialogActions sx={{ justifyContent: { xs: 'center', sm: 'end', md: 'end', lg: 'end', xl: 'end' } }}>
                    <Button onClick={handleCancel} color='secondary'>
                        Cancel
                    </Button>
                    <ButtonWithProgress
                        autoFocus
                        error={Boolean(commentError)}
                        working={posting}
                        sx={{ ml: 1 }}
                        onClick={handlePost}>
                        Post Review
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        </>
    );
};

export interface ExtensionReviewDialogProps {
    extension: Extension;
    reviewPostUrl: string;
}
