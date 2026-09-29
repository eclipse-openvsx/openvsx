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

import { FC } from 'react';
import { Box, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import { Banner } from '../../components/banner';
import { SanitizedMarkdown } from '../../components/sanitized-markdown';
import type { BannerSeverity } from '../../extension-registry-types';

/** Frames the preview so it reads as a rendering of the banner rather than a real one. */
const PreviewFrame = styled(Box, { shouldForwardProp: prop => prop !== 'dimmed' })<{ dimmed?: boolean }>(
    ({ theme, dimmed }) => ({
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadiusCard,
        overflow: 'hidden',
        // Nothing reaches a visitor in this state, so the preview shouldn't look live either.
        opacity: dimmed ? 0.6 : 1,
        // The message is Markdown; drop the paragraph margins so it sits on the banner's line.
        '& p': { marginTop: 0, marginBottom: 0 }
    })
);

const EmptyPreview = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    fontSize: theme.typography.body2.fontSize,
    color: theme.palette.text.disabled
}));

export interface SettingsBannerPreviewProps {
    /** Names what the frame holds: what visitors see now, what they will see, or why they see nothing. */
    caption: string;
    message: string;
    severity: BannerSeverity;
    dimmed?: boolean;
}

export const SettingsBannerPreview: FC<SettingsBannerPreviewProps> = ({ caption, message, severity, dimmed }) => (
    <Box>
        <Typography variant='caption' component='p' color='text.secondary' gutterBottom>
            {caption}
        </Typography>
        <PreviewFrame dimmed={dimmed}>
            {message ? (
                <Banner open color={severity}>
                    <SanitizedMarkdown content={message} linkify={false} />
                </Banner>
            ) : (
                <EmptyPreview>Nothing to preview yet.</EmptyPreview>
            )}
        </PreviewFrame>
    </Box>
);
