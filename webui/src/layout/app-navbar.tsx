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

import { FunctionComponent, useContext, useEffect, useRef, useState } from 'react';
import { AppBar, Box, Toolbar } from '@mui/material';
import { alpha, styled, useTheme } from '@mui/material/styles';
import { Link as RouteLink } from 'react-router';
import { HeaderMenu } from '../header-menu';
import { MainContext } from '../context';
import { usePageSearchBar } from '../context/search/page-search-bar-context';
import { useExtensionTint, useNavbarBlurExtent } from '../context/navbar-chrome-context';
import { OpenVsxMark } from '../components/openvsx-mark';
import { NavSearchField } from './nav-search-field';
import { NAVBAR_HEIGHT_PX } from '../default/theme';

const ToolbarItem = styled(Box)({
    display: 'flex',
    alignItems: 'center'
});

// On-color logo for page bands: brand purple can't be trusted against an
// arbitrary band, so the marks take the inherited content color.
const LOGO_ON_BAND_SX = {
    '& svg path': { fill: 'currentColor' },
    '& svg path:first-of-type': { fillOpacity: 0.72 }
};

// Fade stops sampled from smootherstep — a plain linear fade ends with an
// abrupt slope change the eye reads as a line (Mach banding).
const FADE_EASE = [1, 0.984, 0.897, 0.725, 0.5, 0.275, 0.103, 0.016, 0];

// Progressive blur: each layer anchors to the top and fades out at a staggered
// depth, so a row's blur compounds from the layers still active there (~6px at
// the toolbar, ~0 at the fan's bottom). Saturation rides in the same filters —
// every extra backdrop-filter layer re-snapshots the backdrop each frame.
const BLUR_LAYERS = [
    { blur: '5.2px', saturate: 1.35, fadeStart: 0 },
    { blur: '2.75px', saturate: 1.25, fadeStart: 30 },
    { blur: '1.2px', saturate: 1.2, fadeStart: 60 }
].map(({ blur, saturate, fadeStart }) => ({
    filter: `blur(${blur}) saturate(${saturate})`,
    mask: `linear-gradient(to bottom, ${FADE_EASE.map(
        (k, j) => `rgba(0, 0, 0, ${k}) ${fadeStart + (40 * j) / (FADE_EASE.length - 1)}%`
    ).join(', ')})`
}));

// Nav-side view of the page's tint region: `navTint` is the color the nav
// currently wears — the region's color until its depth scrolls past the nav
// midpoint, then null to return to theme colors. `washColor` lags one step so
// the wash gradient (not transitionable) can still fade out in the last color.
const useNavTint = (): { navTint: string | null; washColor: string | null } => {
    const tint = useExtensionTint();
    const [pastTint, setPastTint] = useState(false);
    useEffect(() => {
        if (!tint) {
            setPastTint(false);
            return;
        }
        const onScroll = () => setPastTint(window.scrollY > tint.depth - NAVBAR_HEIGHT_PX / 2);
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, [tint]);
    const navTint = tint && !pastTint ? tint.color : null;
    const lastTint = useRef(navTint);
    useEffect(() => {
        if (navTint) lastTint.current = navTint;
    }, [navTint]);
    return { navTint, washColor: navTint ?? lastTint.current };
};

export const AppNavbar: FunctionComponent = () => {
    const { pageSettings } = useContext(MainContext);
    const { toolbarContent: ToolbarContent } = pageSettings.elements;
    const { hasPageSearchBar } = usePageSearchBar();
    const theme = useTheme();
    const baseAlpha = theme.palette.mode === 'dark' ? 0.74 : 0.78;
    const navbg = alpha(theme.palette.background.default, baseAlpha);
    // Lighter than the solid glass — the blur fan already provides legibility.
    const tintAlpha = theme.palette.mode === 'dark' ? 0.3 : 0.35;
    const [scrolled, setScrolled] = useState(false);

    useEffect(() => {
        const onScroll = () => setScrolled(window.scrollY > 20);
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    const showSolid = !scrolled;
    const showFan = scrolled;

    const { navTint, washColor } = useNavTint();

    // Pages with pinned sections can deepen the fan so it backs them too — desktop only:
    // on mobile the stretched blur reads as a smear, so pinned rows bring their own glass.
    // Only the lightest layer stretches (backdrop-filter cost scales with area and re-runs
    // every scroll frame); the tint washes are plain gradients and stretch fully.
    const extraBlurDepth = useNavbarBlurExtent();
    const fanBottom = (stretched: boolean) => ({
        xs: '-48px',
        sm: '-80px',
        md: `-${80 + (stretched ? extraBlurDepth : 0)}px`
    });
    const tintBottom = { xs: '-48px', sm: '-150px', md: `-${150 + extraBlurDepth}px` };

    // While a gallery band backs the nav, the chrome wears its color and the
    // content flips to the contrast color, so the two surfaces read as one.
    const contentColor = navTint ? theme.palette.getContrastText(navTint) : theme.palette.text.primary;
    const solidBg = navTint ?? navbg;
    const navBorder = navTint ?? theme.palette.divider;
    // The theme and tint washes cross-fade as separate layers.
    const washGradientOf = (color: string) =>
        `linear-gradient(to bottom, ${FADE_EASE.map(
            (k, i) => `${alpha(color, tintAlpha * k)} ${(i * 100) / (FADE_EASE.length - 1)}%`
        ).join(', ')})`;
    const themeWash = washGradientOf(theme.palette.background.default);
    const tintWash = washColor ? washGradientOf(washColor) : null;

    return (
        <AppBar position='sticky' color='transparent' elevation={0} sx={{ overflow: 'visible', zIndex: 50 }}>
            {/* Solid frosted glass — visible when not scrolled */}
            <Box
                aria-hidden='true'
                sx={{
                    position: 'absolute',
                    inset: 0,
                    pointerEvents: 'none',
                    zIndex: 0,
                    backgroundColor: solidBg,
                    backdropFilter: 'blur(12px) saturate(1.3)',
                    WebkitBackdropFilter: 'blur(12px) saturate(1.3)',
                    borderBottom: '1px solid',
                    borderColor: navBorder,
                    opacity: showSolid ? 1 : 0,
                    // visibility spares the hidden layer's backdrop-filter; it flips
                    // to hidden only after the opacity fade finishes.
                    visibility: showSolid ? 'visible' : 'hidden',
                    transition:
                        'opacity 0.35s ease, visibility 0.35s, background-color 0.35s ease, border-color 0.35s ease'
                }}
            />
            {/* Progressive gradient blur fan — fades in on scroll */}
            {BLUR_LAYERS.map((layer, i) => (
                <Box
                    key={i}
                    aria-hidden='true'
                    sx={{
                        position: 'absolute',
                        left: 0,
                        right: 0,
                        top: 0,
                        bottom: fanBottom(i === BLUR_LAYERS.length - 1),
                        pointerEvents: 'none',
                        zIndex: 0,
                        opacity: showFan ? 1 : 0,
                        visibility: showFan ? 'visible' : 'hidden',
                        transition: 'opacity 0.35s ease, visibility 0.35s, bottom 0.35s ease',
                        backdropFilter: layer.filter,
                        WebkitBackdropFilter: layer.filter,
                        WebkitMaskImage: layer.mask,
                        maskImage: layer.mask
                    }}
                />
            ))}
            <Box
                aria-hidden='true'
                sx={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top: 0,
                    bottom: tintBottom,
                    pointerEvents: 'none',
                    zIndex: 0,
                    opacity: showFan && !navTint ? 1 : 0,
                    transition: 'opacity 0.35s ease, bottom 0.35s ease',
                    background: themeWash
                }}
            />
            {tintWash && (
                <Box
                    aria-hidden='true'
                    sx={{
                        position: 'absolute',
                        left: 0,
                        right: 0,
                        top: 0,
                        bottom: tintBottom,
                        pointerEvents: 'none',
                        zIndex: 0,
                        opacity: showFan && navTint ? 1 : 0,
                        transition: 'opacity 0.35s ease, bottom 0.35s ease',
                        background: tintWash
                    }}
                />
            )}
            <Toolbar
                disableGutters
                sx={{
                    justifyContent: 'space-between',
                    px: { xs: '0.625rem', sm: '1.75rem' },
                    position: 'relative',
                    zIndex: 1,
                    // Inherited by the logo, menu links and kbd chips; portaled
                    // popovers keep theme colors.
                    color: contentColor,
                    transition: 'color 0.35s ease',
                    // Permanent, so the logo reversal also glides on the way back.
                    '& svg path': { transition: 'fill 0.35s ease, fill-opacity 0.35s ease' },
                    // The Publish button inherits too — brand purple can't be
                    // trusted against an arbitrary band.
                    ...(navTint && { '& .MuiButton-textSecondary': { color: 'inherit' } })
                }}>
                <ToolbarItem>
                    {/* Mobile compact icon — shown while the nav search field is visible. The right
                        margin balances the burger button's own padding so the search field sits centered. */}
                    <Box
                        sx={{
                            display: { xs: hasPageSearchBar ? 'none' : 'flex', md: 'none' },
                            alignItems: 'center',
                            mr: '0.5rem',
                            ...(navTint && LOGO_ON_BAND_SX)
                        }}>
                        <RouteLink
                            to='/'
                            aria-label='Home'
                            // Keeps the reversed mark off the browser's link color.
                            style={{ display: 'flex', textDecoration: 'none', color: 'inherit' }}>
                            {/* Same mark height as the full toolbar logo */}
                            <OpenVsxMark style={{ height: '2.5rem' }} />
                        </RouteLink>
                    </Box>
                    {/* Full logo — desktop always, mobile only while the nav search field is hidden. */}
                    <Box
                        sx={{
                            display: { xs: hasPageSearchBar ? 'flex' : 'none', md: 'flex' },
                            ...(navTint && LOGO_ON_BAND_SX)
                        }}>
                        {ToolbarContent ? <ToolbarContent /> : null}
                    </Box>
                </ToolbarItem>
                <NavSearchField />
                <ToolbarItem>
                    <HeaderMenu />
                </ToolbarItem>
            </Toolbar>
        </AppBar>
    );
};
