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

import { FunctionComponent, useEffect, useRef } from 'react';
import { Box, Button, keyframes, Typography } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { isFinished, PublishItem, PublishStatus, usePublishQueue } from '../../context/publish-queue-context';
import { ExtensionCard } from '../extension-card';
import { getExtensionStatus } from '../extension/extension-status';
import { ManageExtensionCard } from '../extension/manage-extension-card';
import { PublishRoutes } from '../../pages/publish/publish-routes';
import { UserSettingsRoutes } from '../../pages/user/user-settings-routes';
import { cardSurface, Eyebrow, TagChip } from '../page-primitives';
import { useSavedFlash } from '../../hooks/use-saved-flash';

/** Matches the listing grid's column so a queued card is the same size as a real one. */
const CARD_WIDTH = '11rem';

// One line, never wrapping: new uploads land on the left and push the rest into the
// scroller, which fades them out on the right instead of ending on a hard edge.
const Scroller = styled(Box)({
    display: 'flex',
    gap: '0.75rem',
    overflowX: 'auto',
    // overflow-x: auto also clips vertically, so pad the scroller by the card's lift and
    // shadow and pull the padding back out again with a matching negative margin.
    padding: '0.75rem 0 1rem',
    margin: '-0.75rem 0 -1rem',
    maskImage: 'linear-gradient(to right, #000 calc(100% - 3rem), transparent)',
    WebkitMaskImage: 'linear-gradient(to right, #000 calc(100% - 3rem), transparent)'
});

const Slot = styled(Box)({
    position: 'relative',
    flex: `0 0 ${CARD_WIDTH}`,
    maxWidth: CARD_WIDTH
});

/** How long the green acknowledgement takes to fade in and back out. */
const ACCEPTED_MS = 1600;

const greenFlash = keyframes`
    0% { opacity: 0; }
    25% { opacity: 1; }
    100% { opacity: 0; }
`;

// Green wash acknowledging that the registry took the package. It sits over the card rather than
// restyling it, so the card's own hover and border transitions are left alone.
const AcceptedGlow = styled('span')(({ theme }) => ({
    position: 'absolute',
    inset: 0,
    pointerEvents: 'none',
    borderRadius: `${theme.shape.borderRadiusCard}px`,
    border: `2px solid ${theme.palette.success.main}`,
    backgroundColor: alpha(theme.palette.success.main, 0.14),
    opacity: 0,
    '@media (prefers-reduced-motion: no-preference)': {
        animation: `${greenFlash} ${ACCEPTED_MS}ms ease-in-out`
    }
}));

// Loud enough to read at a glance. Wraps because it carries whatever the extension lists call the
// state, and the longest of those does not fit a card-width column on one line.
const StatusChip = styled(TagChip)({
    fontSize: '0.5625rem',
    flexShrink: 1,
    maxWidth: '100%',
    whiteSpace: 'normal',
    textAlign: 'center',
    lineHeight: 1.4
});

/** What the queue as a whole is waiting on: uploads first, then the registry's checks. */
const queueLabel = (items: PublishItem[]): string => {
    const count = (status: PublishStatus) => items.filter(item => item.status === status).length;
    const checking = count('checking');
    if (checking > 0) {
        return `Checking ${checking}`;
    }
    const uploading = count('uploading');
    if (uploading > 0) {
        return `Publishing ${uploading}`;
    }
    const reviewing = count('reviewing');
    if (reviewing > 0) {
        return `Reviewing ${reviewing}`;
    }
    // Nothing left in flight, so the queue reports whether it all landed or something is waiting on the user.
    return count('published') === items.length ? 'Published' : 'Action needed';
};

/** Card-shaped stand-in for a package that never made it into the registry. */
const FailedCard = styled(Box)(({ theme }) => ({
    ...cardSurface(theme),
    borderColor: alpha(theme.palette.error.main, 0.4),
    height: '100%',
    minHeight: '13rem',
    padding: '1rem 0.875rem',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.5rem',
    textAlign: 'center'
}));

const QueueCard: FunctionComponent<{ item: PublishItem }> = ({ item }) => {
    if (item.status === 'failed' || item.status === 'rejected') {
        return (
            <FailedCard title={item.error}>
                <ErrorOutlineIcon sx={{ fontSize: '1.5rem', color: 'error.main' }} />
                <Typography noWrap sx={{ width: '100%', fontSize: '0.8125rem', fontWeight: 700 }}>
                    {item.fileName}
                </Typography>
                <Typography
                    sx={{
                        fontSize: '0.75rem',
                        color: 'error.main',
                        display: '-webkit-box',
                        WebkitLineClamp: 3,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden'
                    }}>
                    {item.error ?? 'Publishing failed'}
                </Typography>
            </FailedCard>
        );
    }

    // No extension yet means the upload is still in flight, and the card renders its skeleton.
    if (!item.extension) {
        return <ExtensionCard />;
    }
    // The registry's own verdict, worded by the helper the extension lists use: the queue's statuses
    // track the upload, and an extension can be held back for reasons that outlive it.
    const status = getExtensionStatus(item.extension);
    // Once published it is the user's to manage, so it gets the gear and the settings route.
    return (
        <ManageExtensionCard
            extension={item.extension}
            routePrefix={UserSettingsRoutes.EXTENSIONS}
            iconPending={item.awaitingIcon}
            linkState={{ backTo: PublishRoutes.ROOT, backLabel: 'Back to publishing' }}
            footerStart={status ? <StatusChip sx={{ color: status.color }}>{status.label}</StatusChip> : undefined}
        />
    );
};

/**
 * One card in the line, greeting acceptance with a green wash. It is tied to the transition rather
 * than the status, so returning to the page later does not replay it.
 */
const QueueSlot: FunctionComponent<{ item: PublishItem }> = ({ item }) => {
    const { saved: accepted, flash } = useSavedFlash(ACCEPTED_MS);
    const previousStatus = useRef(item.status);

    useEffect(() => {
        const justPublished = previousStatus.current !== 'published' && item.status === 'published';
        previousStatus.current = item.status;
        if (justPublished) {
            flash();
        }
    }, [item.status, flash]);

    return (
        <Slot>
            <QueueCard item={item} />
            {accepted ? <AcceptedGlow data-testid='publish-accepted' /> : null}
        </Slot>
    );
};

/**
 * The packages being published, as the same cards the listings use — a skeleton
 * while a package uploads, the real card once the registry accepts it. Newest
 * first, so a fresh upload always appears in the same place.
 */
export const PublishQueueStrip: FunctionComponent = () => {
    const { items, clearFinished } = usePublishQueue();
    if (items.length === 0) {
        return null;
    }

    return (
        <Box aria-label='Publishing queue'>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '0.5rem',
                    mb: '0.75rem'
                }}>
                <Eyebrow>{queueLabel(items)}</Eyebrow>
                {items.some(isFinished) ? (
                    <Button size='small' onClick={clearFinished}>
                        Clear
                    </Button>
                ) : null}
            </Box>
            <Scroller>
                {items.map(item => (
                    <QueueSlot key={item.id} item={item} />
                ))}
            </Scroller>
        </Box>
    );
};
