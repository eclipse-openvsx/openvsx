/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { Box, Typography } from '@mui/material';
import { alpha, CSSObject, styled, Theme } from '@mui/material/styles';
import { MONO_FONT } from '../default/theme';

/** Normalized gap between stacked sections; the owl selector skips the first (and any null) child. */
export const SectionStack = styled(Box)(({ theme }) => ({
    '& > * + *': {
        marginTop: '2.5rem',
        [theme.breakpoints.down('sm')]: {
            marginTop: '1.5rem'
        }
    }
}));

/** Full-bleed hairline dividing stacked page sections, VS Code workbench style. */
export const SectionSeparator = styled(Box)(({ theme }) => ({
    borderTop: `1px solid ${theme.palette.border2}`
}));

/** Extension-card grid shared by the listings; column count falls out of available
 *  width. Sparse grids keep auto-fill's empty tracks so few results stay card-sized;
 *  with 7+ items (a curated row or a full results page) auto-fit collapses leftover
 *  tracks so rows stretch to fill the width. */
export const ExtensionGrid = styled(Box)({
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(145px, 1fr))',
    gap: '0.75rem',
    '&:has(> :nth-of-type(7))': {
        gridTemplateColumns: 'repeat(auto-fit, minmax(145px, 1fr))'
    }
});

/** Leading '/' glyph of the search fields — the field's mark and the shortcut hint in one. */
export const MonoSlash = styled('span')(({ theme }) => ({
    fontFamily: MONO_FONT,
    color: theme.palette.secondary.light,
    fontSize: '1.0625rem',
    lineHeight: 1,
    flexShrink: 0,
    userSelect: 'none'
}));

/** Small uppercase label used to head sections, columns and sidebars. */
export const Eyebrow = styled(Typography)(({ theme }) => ({
    fontSize: '0.75rem',
    fontWeight: 700,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    color: theme.palette.text.disabled
})) as typeof Typography;

/**
 * Elevated surface shared by cards (extensions, categories, panels): paper
 * background, hairline border and the card corner radius from the theme.
 */
export const cardSurface = (theme: Theme) => ({
    backgroundColor: theme.palette.background.paper,
    border: `1px solid ${theme.palette.divider}`,
    // px string: the sx system scales bare numbers by the theme's base radius
    borderRadius: `${theme.shape.borderRadiusCard}px`
});

/** Accent focus ring shared by the search fields and card links. */
export const focusRing = (theme: Theme, extraShadow?: string) => ({
    borderColor: theme.palette.secondary.main,
    boxShadow: `0 0 0 3px ${alpha(theme.palette.secondary.main, 0.16)}${extraShadow ? `, ${extraShadow}` : ''}`
});

/**
 * Keyboard-focus outline for buttons, pills and links. ButtonBase resets the
 * native outline, so anything built on it needs this to be reachable by keyboard.
 */
export const focusOutline = (theme: Theme) => ({
    '&.Mui-focusVisible, &:focus-visible': {
        outline: `2px solid ${theme.palette.secondary.main}`,
        outlineOffset: '2px'
    }
});

/** Pill anatomy shared by the tab strips and pill buttons; callers add their own fill and active state. */
export const pillSurface = (theme: Theme) => ({
    padding: '0.4375rem 0.8125rem',
    borderRadius: theme.shape.borderRadiusPill,
    border: `1px solid ${theme.palette.divider}`,
    color: theme.palette.text.secondary,
    fontSize: '0.8125rem',
    fontWeight: 500,
    transition: 'border-color 0.14s, color 0.14s, background 0.14s'
});

/** Tiny uppercase tag (member roles, version states); sized by `fontSize`, tinted with the accent when `accent`. */
export const TagChip = styled('span', { shouldForwardProp: prop => prop !== 'accent' })<{ accent?: boolean }>(
    ({ theme, accent }) => ({
        flexShrink: 0,
        display: 'inline-flex',
        alignItems: 'center',
        padding: '0.38em 1.05em',
        borderRadius: theme.shape.borderRadiusPill,
        fontSize: '0.65625rem',
        fontWeight: 700,
        letterSpacing: '0.05em',
        textTransform: 'uppercase',
        backgroundColor: accent ? theme.palette.accentSoft : theme.palette.surface3,
        color: accent ? theme.palette.secondary.light : theme.palette.text.disabled
    })
);

/**
 * Translucent glass fill for controls floating over scrolled content (pills, pinned toolbars).
 * Backdrop filters re-blur on every scrolled frame, so keep the frosted surfaces small.
 */
export const glassSurface = (theme: Theme, opacity = 0.7) => ({
    backgroundColor: alpha(theme.palette.surface2, opacity),
    backdropFilter: 'blur(2px) saturate(1.8)'
});

/**
 * Compact toolbar control (the "Sort by" select, filter autocompletes): one 30px text row on a
 * paper fill. Selects take it directly via sx; composite inputs spread it into their outlined
 * root. It carries nested selectors for the select's inner slots, so it belongs in sx (or another
 * emotion context) and will not work in a plain style prop.
 */
export const compactControl = (theme: Theme) => ({
    fontSize: '0.8125rem',
    fontWeight: 500,
    color: theme.palette.text.primary,
    height: '1.875rem',
    backgroundColor: theme.palette.background.paper,
    borderRadius: `${theme.shape.borderRadius}px`,
    '& .MuiSelect-select': { padding: '0.25rem 2rem 0.25rem 0.625rem' },
    '& .MuiSelect-icon': { color: theme.palette.text.disabled, fontSize: '1.125rem' }
});

/** Hover treatment for chips and pills: accent border and text color. Suppressed on touch devices. */
export const accentHover = (theme: Theme) => ({
    '@media (hover: hover)': {
        '&:hover': {
            borderColor: theme.palette.secondary.main,
            color: theme.palette.secondary.light
        }
    }
});

/** Hover treatment for interactive cards: accent border, shadow and lift. Suppressed on touch devices. */
export const cardHoverLift = (theme: Theme, extra?: CSSObject) => ({
    // `extra` joins this block rather than the caller adding its own: a second
    // '@media (hover: hover)' key in the same object replaces this one instead of merging.
    '@media (hover: hover)': {
        '&:hover': {
            borderColor: theme.palette.secondary.main,
            boxShadow: 'var(--shadow)',
            transform: 'translateY(-2px)'
        },
        ...extra
    }
});
