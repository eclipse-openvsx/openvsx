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

import { FC, useId } from 'react';
import {
    Box,
    Checkbox,
    FormControlLabel,
    Skeleton,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import type { BannerSeverity, SiteSettings } from '../../extension-registry-types';
import { SettingsBannerPreview } from './settings-banner-preview';
import { SettingsSwitch } from './settings-switch';

// Mirrors SettingsValidator on the server, which refuses a longer message.
const MAX_MESSAGE_LENGTH = 1000;

const SEVERITIES: { value: BannerSeverity; label: string; Icon: typeof InfoOutlinedIcon }[] = [
    { value: 'info', label: 'Info', Icon: InfoOutlinedIcon },
    { value: 'warning', label: 'Warning', Icon: WarningAmberRoundedIcon }
];

/** What the draft would put in front of a visitor. Every line of copy below is derived from it. */
type BannerState = 'live' | 'empty' | 'off';

const previewCaption = (state: BannerState, changed: boolean): string => {
    switch (state) {
        case 'live':
            return changed
                ? 'Preview - what visitors will see after you save'
                : 'Preview - what visitors see right now';
        case 'empty':
            return 'Preview - nothing is shown while the message is empty';
        case 'off':
            return 'Preview - nobody sees this while the banner is off';
    }
};

/** Spells out who the save reaches, so the consequence is read right before the Save button. */
const saveOutcome = (
    state: BannerState,
    canShowAgain: boolean,
    showAgain: boolean
): { Icon: typeof VisibilityOutlinedIcon; text: string } => {
    switch (state) {
        case 'off':
            return { Icon: VisibilityOffOutlinedIcon, text: 'nobody will see a banner.' };
        case 'empty':
            return { Icon: VisibilityOffOutlinedIcon, text: 'nothing will be shown.' };
        case 'live':
            if (!canShowAgain) {
                return { Icon: VisibilityOutlinedIcon, text: 'everyone will see this banner.' };
            }
            return showAgain
                ? {
                      Icon: VisibilityOutlinedIcon,
                      text: 'everyone will see this banner, including the people who dismissed it.'
                  }
                : {
                      Icon: VisibilityOffOutlinedIcon,
                      text: 'people who already dismissed this banner will not see it again.'
                  };
    }
};

const CardBody = styled(Box)(({ theme }) => ({
    padding: theme.spacing(3),
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2.5)
}));

const HeaderRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: theme.spacing(2),
    flexWrap: 'wrap'
}));

/** The severity toggles and, while the offer stands, who the save shows the banner to. */
const ControlsRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: theme.spacing(2),
    flexWrap: 'wrap'
}));

const OutcomeRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    padding: theme.spacing(1.5, 3),
    borderTop: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.bg2
}));

export interface SettingsBannerItemProps {
    settings: SiteSettings;
    /** The draft banner differs from the saved one, so saving would change what visitors see. */
    changed?: boolean;
    /** Whether showing the banner again to the people who dismissed it is the admin's to choose. */
    canShowAgain?: boolean;
    showAgain?: boolean;
    disabled?: boolean;
    loading?: boolean;
    onChange: (patch: SiteSettings) => void;
    onShowAgainChange: (showAgain: boolean) => void;
}

export const SettingsBannerItem: FC<SettingsBannerItemProps> = ({
    settings,
    changed = false,
    canShowAgain = false,
    showAgain = true,
    disabled,
    loading,
    onChange,
    onShowAgainChange
}) => {
    const severityLabelId = useId();
    const enabled = settings.bannerEnabled ?? false;
    const text = settings.bannerMessage ?? '';
    // The server keeps a legacy severity it no longer accepts, and `Banner` has no variant for it.
    const severity = settings.bannerSeverity === 'warning' ? 'warning' : 'info';
    const message = text.trim();

    if (loading) {
        return (
            <Box sx={{ p: 3 }}>
                <Skeleton variant='rounded' height={280} />
            </Box>
        );
    }

    const state: BannerState = !enabled ? 'off' : message ? 'live' : 'empty';
    const { Icon: OutcomeIcon, text: outcome } = saveOutcome(state, canShowAgain, showAgain);

    return (
        <>
            <CardBody>
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
                        onChange={(_event, checked) => onChange({ bannerEnabled: checked })}
                    />
                </HeaderRow>

                <SettingsBannerPreview
                    caption={previewCaption(state, changed)}
                    message={message}
                    severity={severity}
                    dimmed={state !== 'live'}
                />

                <TextField
                    label='Message'
                    value={text}
                    onChange={event => onChange({ bannerMessage: event.target.value })}
                    disabled={disabled}
                    helperText={`Markdown is supported, HTML is not. ${text.length}/${MAX_MESSAGE_LENGTH}`}
                    inputProps={{ maxLength: MAX_MESSAGE_LENGTH }}
                    multiline
                    minRows={2}
                    fullWidth
                />

                <ControlsRow>
                    <Box>
                        <Typography variant='caption' component='p' color='text.secondary' id={severityLabelId}>
                            Severity
                        </Typography>
                        <ToggleButtonGroup
                            exclusive
                            size='small'
                            aria-labelledby={severityLabelId}
                            value={severity}
                            disabled={disabled}
                            onChange={(_event, value: BannerSeverity | null) =>
                                value && onChange({ bannerSeverity: value })
                            }>
                            {SEVERITIES.map(({ value, label, Icon }) => (
                                <ToggleButton key={value} value={value} sx={{ gap: 1, px: 2 }}>
                                    <Icon fontSize='small' />
                                    {label}
                                </ToggleButton>
                            ))}
                        </ToggleButtonGroup>
                    </Box>

                    {canShowAgain && (
                        <FormControlLabel
                            sx={{ mr: 0 }}
                            control={
                                <Checkbox
                                    color='secondary'
                                    checked={showAgain}
                                    disabled={disabled}
                                    onChange={(_event, checked) => onShowAgainChange(checked)}
                                />
                            }
                            label='Show again to everyone who dismissed it'
                            slotProps={{ typography: { variant: 'body2' } }}
                        />
                    )}
                </ControlsRow>
            </CardBody>

            {/* Always mounted, so a screen reader announces the sentence appearing inside it. */}
            <Box aria-live='polite'>
                {changed && (
                    <OutcomeRow>
                        <OutcomeIcon fontSize='small' color='action' />
                        <Typography variant='body2'>
                            <strong>On save:</strong> {outcome}
                        </Typography>
                    </OutcomeRow>
                )}
            </Box>
        </>
    );
};
