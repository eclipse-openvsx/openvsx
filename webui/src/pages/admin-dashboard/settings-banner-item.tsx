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
import { Box, Button, Skeleton, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import { Banner } from '../../components/banner';
import { SanitizedMarkdown } from '../../components/sanitized-markdown';
import type { BannerSeverity, Settings } from '../../extension-registry-types';
import { SettingsSwitch } from './settings-switch';

// Mirrors SettingsValidator on the server, which refuses a longer message.
const MAX_MESSAGE_LENGTH = 1000;

const SEVERITIES: { value: BannerSeverity; label: string; Icon: typeof InfoOutlinedIcon }[] = [
    { value: 'info', label: 'Info', Icon: InfoOutlinedIcon },
    { value: 'warning', label: 'Warning', Icon: WarningAmberRoundedIcon }
];

/** Frames the preview so it reads as a rendering of the banner rather than a real one. */
const PreviewFrame = styled(Box)(({ theme }) => ({
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadiusCard,
    overflow: 'hidden',
    // The message is Markdown; drop the paragraph margins so it sits on the banner's line.
    '& p': { marginTop: 0, marginBottom: 0 }
}));

const EmptyPreview = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    fontSize: theme.typography.body2.fontSize,
    color: theme.palette.text.disabled
}));

const HeaderRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: theme.spacing(2),
    flexWrap: 'wrap'
}));

const FooterRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: theme.spacing(2),
    flexWrap: 'wrap',
    borderTop: `1px solid ${theme.palette.divider}`,
    paddingTop: theme.spacing(2)
}));

export interface SettingsBannerItemProps {
    settings: Settings;
    /** The draft already carries a fresh dismiss token, waiting to be saved. */
    resetPending?: boolean;
    disabled?: boolean;
    loading?: boolean;
    onChange: (patch: Settings) => void;
}

export const SettingsBannerItem: FC<SettingsBannerItemProps> = ({
    settings,
    resetPending,
    disabled,
    loading,
    onChange
}) => {
    const enabled = settings['banner-enabled'] ?? false;
    const text = settings['banner-message'] ?? '';
    const severity = settings['banner-severity'] ?? 'info';
    const message = text.trim();

    if (loading) {
        return (
            <Box sx={{ p: 3 }}>
                <Skeleton variant='rounded' height={220} />
            </Box>
        );
    }

    return (
        <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <HeaderRow>
                <Box>
                    <Typography variant='subtitle1'>Banner</Typography>
                    <Typography variant='body2' color='text.secondary'>
                        A notice above the navbar on every page - maintenance windows, incidents, announcements.
                    </Typography>
                </Box>
                <SettingsSwitch
                    name='banner'
                    checked={enabled}
                    disabled={disabled}
                    onChange={(_event, checked) => onChange({ 'banner-enabled': checked })}
                />
            </HeaderRow>

            <ToggleButtonGroup
                exclusive
                size='small'
                value={severity}
                disabled={disabled}
                onChange={(_event, value: BannerSeverity | null) => value && onChange({ 'banner-severity': value })}>
                {SEVERITIES.map(({ value, label, Icon }) => (
                    <ToggleButton key={value} value={value} sx={{ gap: 1, px: 2 }}>
                        <Icon fontSize='small' />
                        {label}
                    </ToggleButton>
                ))}
            </ToggleButtonGroup>

            <TextField
                label='Message'
                value={text}
                onChange={event => onChange({ 'banner-message': event.target.value })}
                disabled={disabled}
                helperText={`Markdown is supported, HTML is not. ${text.length}/${MAX_MESSAGE_LENGTH}`}
                inputProps={{ maxLength: MAX_MESSAGE_LENGTH }}
                multiline
                minRows={2}
                fullWidth
            />

            <Box>
                <Typography variant='overline' color='text.secondary'>
                    Preview
                    {enabled ? '' : ' - turned off, nobody sees it'}
                </Typography>
                <PreviewFrame>
                    {message ? (
                        <Banner open color={severity}>
                            <SanitizedMarkdown content={message} linkify={false} />
                        </Banner>
                    ) : (
                        <EmptyPreview>Nothing to preview yet.</EmptyPreview>
                    )}
                </PreviewFrame>
            </Box>

            <FooterRow>
                <Typography variant='body2' color='text.secondary'>
                    {resetPending
                        ? 'Saving will show the banner again to everyone who dismissed it.'
                        : 'Correcting the message leaves the banner hidden for anyone who dismissed it.'}
                </Typography>
                <Button
                    variant='outlined'
                    disabled={disabled || resetPending || message.length === 0}
                    onClick={() => onChange({ 'banner-dismiss-id': crypto.randomUUID() })}>
                    Show again to everyone
                </Button>
            </FooterRow>
        </Box>
    );
};
