/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, PropsWithChildren } from 'react';
import { Box, Button, Collapse, IconButton } from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import CloseIcon from '@mui/icons-material/Close';

const VARIANTS = {
    info: { Icon: InfoOutlinedIcon, bg: 'accentSoft', accent: 'secondary.light' },
    warning: { Icon: WarningAmberRoundedIcon, bg: 'warningSoft', accent: 'warningAccent' }
} as const;

export const Banner: FunctionComponent<PropsWithChildren<BannerProps>> = props => {
    const { color = 'info', open, showDismissButton, dismissButtonLabel, dismissButtonOnClick, children } = props;
    const { Icon, bg, accent } = VARIANTS[color];
    return (
        <Collapse in={open} timeout={{ enter: 800, exit: 300 }}>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '1rem',
                    minHeight: '2.25rem',
                    py: '0.375rem',
                    // Left/right edges line up with the navbar content.
                    px: { xs: '0.625rem', sm: '1.75rem' },
                    bgcolor: bg,
                    color: 'text.primary',
                    // The link in the consumer content follows the tone's accent.
                    '& a': {
                        color: accent,
                        fontWeight: 700,
                        textDecoration: 'none',
                        '&:hover': { textDecoration: 'underline' }
                    }
                }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.5rem', minWidth: 0 }}>
                    <Icon sx={{ color: accent, fontSize: '1.125rem', flexShrink: 0 }} />
                    <Box component='span' sx={{ fontSize: '0.875rem', lineHeight: 1.4 }}>
                        {children}
                    </Box>
                </Box>
                {showDismissButton &&
                    (dismissButtonLabel ? (
                        <Button
                            variant='text'
                            onClick={dismissButtonOnClick}
                            sx={theme => ({
                                flexShrink: 0,
                                color: accent,
                                px: 2,
                                py: 0.5,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                whiteSpace: 'nowrap',
                                borderRadius: `${theme.shape.borderRadius}px`,
                                '&:hover': { backgroundColor: 'color-mix(in srgb, currentColor 8%, transparent)' }
                            })}>
                            {dismissButtonLabel}
                        </Button>
                    ) : (
                        <IconButton
                            size='small'
                            aria-label='Dismiss'
                            onClick={dismissButtonOnClick}
                            sx={{ flexShrink: 0, color: 'inherit', mr: '-0.375rem' }}>
                            <CloseIcon sx={{ fontSize: '1.125rem' }} />
                        </IconButton>
                    ))}
            </Box>
        </Collapse>
    );
};

export interface BannerProps {
    open: boolean;
    showDismissButton?: boolean;
    dismissButtonLabel?: string;
    dismissButtonOnClick?: () => void;
    color?: 'info' | 'warning';
}
