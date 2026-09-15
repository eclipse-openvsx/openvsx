/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, RefObject, useCallback, useContext, useEffect, useRef } from 'react';
import {
    Typography,
    Box,
    Container,
    Link,
    Avatar,
    Paper,
    Badge,
    Stack,
    useTheme,
    PaletteMode,
    decomposeColor
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { Link as RouteLink, Route, Routes, useNavigate, useParams } from 'react-router';
import { MainContext } from '../../context';
import { createRoute, formatCompactNumber } from '../../utils';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { ExtensionIcon } from '../../components/extension/extension-icon';
import { HoverPopover } from '../../components/hover-popover';
import { Extension, UserData } from '../../extension-registry-types';
import { TextDivider } from '../../components/text-divider';
import { ExtensionRatingStars } from './extension-rating-stars';
import { NamespaceDetailRoutes } from '../namespace-detail/namespace-detail-routes';
import { ExtensionDetailOverview } from './extension-detail-overview';
import { ExtensionDetailChanges } from './extension-detail-changes';
import { ExtensionDetailReviews } from './extension-detail-reviews';

import { ExtensionDetailRoutes } from './extension-detail-routes';
import { TrustedPublishingIcon } from './trusted-publishing-icon';
import { useExtensionDetail } from './use-extension-details';
import { KbdKey } from '../../components/kbd-key';
import { useShortcut } from '../../hooks/use-shortcut';
import { NAVBAR_HEIGHT, NAVBAR_HEIGHT_PX } from '../../default/theme';
import { useSetExtensionTint } from '../../context/navbar-chrome-context';
import { PageContainer } from '../../components/page-container';
import { PillTab, PillTabs } from '../../components/pill-tabs';
import SaveAltIcon from '@mui/icons-material/SaveAlt';
import WarningIcon from '@mui/icons-material/Warning';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';

const inlineLinkStyle = {
    display: 'contents',
    cursor: 'pointer',
    textDecoration: 'none',
    '&:hover': { textDecoration: 'underline' }
} as const;

const StyledRouteLink = styled(RouteLink)(inlineLinkStyle);
const StyledLink = styled(Link)(inlineLinkStyle);
const StyledHoverPopover = styled(HoverPopover)({ display: 'flex', alignItems: 'center' });
const PreviewBadge = styled(Badge)(({ theme }) => ({
    '& .MuiBadge-badge': { top: theme.spacing(1), right: theme.spacing(-5) }
}));

enum ExtensionTab {
    OVERVIEW = 'overview',
    CHANGES = 'changes',
    REVIEWS = 'reviews'
}

const TAB_VALUES = new Set<string>(Object.values(ExtensionTab));

const isTabSegment = (segment?: string): segment is ExtensionTab => TAB_VALUES.has(segment ?? '');

const parseTab = (segment?: string): ExtensionTab => (isTabSegment(segment) ? segment : ExtensionTab.OVERVIEW);

const buildExtensionPath = (namespace: string, name: string, target?: string, ...extra: string[]) => {
    const arr = [ExtensionDetailRoutes.ROOT, namespace, name];
    if (target) arr.push(target);
    arr.push(...extra);
    return createRoute(arr);
};

const UnverifiedBanner: FunctionComponent<{
    extension: Extension;
    headerTextColor: string;
    themeType: PaletteMode;
}> = ({ extension, headerTextColor, themeType }) => {
    const { pageSettings } = useContext(MainContext);

    if (extension.verified) return null;

    return (
        <Paper
            sx={{
                display: 'flex',
                maxWidth: '800px',
                p: 2,
                mt: 0,
                mx: { xs: 0, md: 6 },
                mb: { xs: 2, md: 4 },
                bgcolor: `warning.${themeType}`,
                color: headerTextColor,
                '& a': { color: headerTextColor, textDecoration: 'underline' }
            }}>
            <WarningIcon fontSize='large' />
            <Box ml={1}>
                This version of the &ldquo;{extension.displayName ?? extension.name}&rdquo; extension was published by{' '}
                <Link href={extension.publishedBy.homepage}>{extension.publishedBy.loginName}</Link>. That user account
                is not a verified publisher of the namespace &ldquo;{extension.namespace}&rdquo; of this extension.{' '}
                <Link href={pageSettings.urls.namespaceAccessInfo} target='_blank'>
                    See the documentation
                </Link>{' '}
                to learn how we handle namespaces and what you can do to eliminate this warning.
            </Box>
        </Paper>
    );
};

const VerificationIcon: FunctionComponent<{
    verified: boolean;
    color: string;
}> = ({ verified, color }) => {
    const { pageSettings } = useContext(MainContext);
    const icon = verified ? <VerifiedUserIcon fontSize='small' /> : <WarningIcon fontSize='small' />;
    const title = verified ? 'Verified publisher' : 'Unverified publisher';

    return (
        <StyledLink href={pageSettings.urls.namespaceAccessInfo} target='_blank' title={title} sx={{ color }}>
            {icon}
        </StyledLink>
    );
};

const UserPopover: FunctionComponent<{
    user: UserData;
    color: string;
}> = ({ user, color }) => {
    const popupContent = (
        <Box display='flex' flexDirection='row'>
            {user.avatarUrl && (
                <Avatar
                    src={user.avatarUrl}
                    alt={user.fullName ?? user.loginName}
                    variant='rounded'
                    sx={{ width: '60px', height: '60px' }}
                />
            )}
            <Box ml={2}>
                {user.fullName && <Typography variant='h6'>{user.fullName}</Typography>}
                <Typography variant='body1'>{user.loginName}</Typography>
            </Box>
        </Box>
    );

    return (
        <StyledHoverPopover id={`user_${user.loginName}_popover`} popupContent={popupContent}>
            <StyledLink href={user.homepage} sx={{ color }}>
                {user.avatarUrl ? (
                    <>
                        {user.loginName}&nbsp;
                        <Avatar src={user.avatarUrl} alt={user.loginName} sx={{ width: '20px', height: '20px' }} />
                    </>
                ) : (
                    user.loginName
                )}
            </StyledLink>
        </StyledHoverPopover>
    );
};

const LicenseLink: FunctionComponent<{
    extension: Extension;
    color: string;
}> = ({ extension, color }) => {
    if (extension.files.license) {
        return (
            <StyledLink
                href={extension.files.license}
                sx={{ color }}
                title={extension.license ? 'License type' : undefined}>
                {extension.license || 'Provided license'}
            </StyledLink>
        );
    }
    return <>{extension.license || 'Unlicensed'}</>;
};

export const ExtensionHeaderInfo: FunctionComponent<{
    extension: Extension;
    headerTextColor: string;
}> = ({ extension, headerTextColor }) => {
    const downloadCountFormatted = formatCompactNumber(extension.downloadCount || 0);
    const reviewCountFormatted = formatCompactNumber(extension.reviewCount || 0);

    return (
        <Box sx={{ pt: 0.5, overflow: 'visible', flex: 1, minWidth: 0 }}>
            <PreviewBadge color='secondary' badgeContent='Preview' invisible={!extension.preview}>
                <Typography
                    component='h1'
                    sx={{
                        fontSize: { xs: '1.7rem', md: '1.938rem' },
                        fontWeight: 800,
                        letterSpacing: '-0.025em',
                        mb: 1,
                        color: headerTextColor
                    }}>
                    {extension.displayName ?? extension.name}
                </Typography>
            </PreviewBadge>

            {extension.deprecated && (
                <Stack direction='row' alignItems='center'>
                    <WarningIcon fontSize='small' />
                    <Typography>
                        This extension has been deprecated.
                        {extension.replacement && (
                            <>
                                &nbsp;Use{' '}
                                <StyledLink sx={{ color: headerTextColor }} href={extension.replacement.url}>
                                    {extension.replacement.displayName}
                                </StyledLink>{' '}
                                instead.
                            </>
                        )}
                    </Typography>
                </Stack>
            )}

            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    color: headerTextColor,
                    flexDirection: { xs: 'column', md: 'row' }
                }}>
                <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                    <VerificationIcon verified={extension.verified} color={headerTextColor} />
                    &nbsp;
                    <StyledRouteLink
                        to={createRoute([NamespaceDetailRoutes.ROOT, extension.namespace])}
                        style={{ color: headerTextColor }}>
                        {extension.namespaceDisplayName}
                    </StyledRouteLink>
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall />
                <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                    Published by&nbsp;
                    <UserPopover user={extension.publishedBy} color={headerTextColor} />
                    {extension.publishedWithTrustedPublishing && (
                        <>
                            <TextDivider backgroundColor={headerTextColor} collapseSmall />
                            <TrustedPublishingIcon color={headerTextColor} />
                        </>
                    )}
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall />
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                    <LicenseLink extension={extension} color={headerTextColor} />
                </Box>
            </Box>

            <Box mt={2} mb={2} overflow='auto'>
                <Typography sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{extension.description}</Typography>
            </Box>

            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    color: headerTextColor,
                    justifyContent: { xs: 'center', md: 'flex-start' }
                }}>
                <Box
                    component='span'
                    sx={{ display: 'flex', alignItems: 'center' }}
                    title={
                        extension.downloadCount && extension.downloadCount >= 1000
                            ? `${extension.downloadCount} downloads`
                            : undefined
                    }>
                    <SaveAltIcon fontSize='small' />
                    &nbsp;{downloadCountFormatted}&nbsp;{extension.downloadCount === 1 ? 'download' : 'downloads'}
                </Box>
                <TextDivider backgroundColor={headerTextColor} />
                <StyledLink
                    href={createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name, 'reviews'])}
                    sx={{ display: 'flex', alignItems: 'center', color: headerTextColor }}
                    title={
                        extension.averageRating === undefined
                            ? 'Not rated yet'
                            : `Average rating: ${Math.round(extension.averageRating * 10) / 10} out of 5 (${extension.reviewCount} reviews)`
                    }>
                    <ExtensionRatingStars number={extension.averageRating ?? 0} fontSize='small' />(
                    {reviewCountFormatted})
                </StyledLink>
            </Box>
        </Box>
    );
};

// The luma CSS grayscale() resolves a color to; deprecated bands paint through that filter.
const grayscaleOf = (color: string): string => {
    const [r, g, b] = decomposeColor(color).values;
    const y = Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b);
    return `rgb(${y}, ${y}, ${y})`;
};

// Declares the header band as the nav's tint region: its gallery color (null
// for default bands) and how deep it runs. The nav does the switching; the
// resize observer keeps the depth fresh as the band's content reflows.
const useNavTintFromBand = (color: string | null, bandRef: RefObject<HTMLDivElement>): void => {
    const setTint = useSetExtensionTint();
    useEffect(() => {
        const band = bandRef.current;
        if (!band) return;
        // Fires once on observe, then on size changes.
        const observer = new ResizeObserver(() =>
            setTint({ color, depth: band.getBoundingClientRect().bottom + window.scrollY })
        );
        observer.observe(band);
        return () => {
            observer.disconnect();
            setTint(null);
        };
    }, [color, bandRef, setTint]);
};

const ExtensionHeader: FunctionComponent<{
    extension: Extension;
    bandRef: RefObject<HTMLDivElement>;
}> = ({ extension, bandRef }) => {
    const theme = useTheme();
    const { pageSettings } = useContext(MainContext);

    const themeType = (extension.galleryTheme || pageSettings.themeType) ?? 'light';
    const fallbackColor = theme.palette.neutral[themeType] as string;
    let headerColor = extension.galleryColor || fallbackColor;

    try {
        decomposeColor(headerColor);
    } catch {
        headerColor = fallbackColor;
    }

    const headerTextColor = theme.palette.getContrastText(headerColor);
    const usesDefaultBg = !extension.galleryColor;

    // Only real gallery colors tint the nav; over default bands it keeps its
    // theme glass, which is visually flush with bg2 already.
    useNavTintFromBand(usesDefaultBg ? null : extension.deprecated ? grayscaleOf(headerColor) : headerColor, bandRef);

    return (
        <Box
            ref={bandRef}
            sx={{
                bgcolor: usesDefaultBg ? 'bg2' : headerColor,
                color: usesDefaultBg ? 'text.primary' : headerTextColor,
                // Extend the band under the sticky nav so its glass picks up the header color.
                mt: `-${NAVBAR_HEIGHT}`,
                pt: NAVBAR_HEIGHT,
                borderBottom: '1px solid',
                borderColor: 'divider',
                filter: extension.deprecated ? 'grayscale(100%)' : undefined
            }}>
            <Container maxWidth='xl'>
                <Box sx={{ pt: '1.125rem', pb: '1.75rem' }}>
                    <UnverifiedBanner
                        extension={extension}
                        headerTextColor={usesDefaultBg ? theme.palette.text.primary : headerTextColor}
                        themeType={themeType}
                    />
                    <Box
                        sx={{
                            display: 'flex',
                            width: '100%',
                            flexDirection: { xs: 'column', md: 'row' },
                            textAlign: { xs: 'center', md: 'start' },
                            alignItems: { xs: 'center', md: 'flex-start' },
                            gap: { xs: 2, md: '1.625rem' }
                        }}>
                        <ExtensionIcon
                            extension={extension}
                            sx={{ width: '96px', height: '96px', flexShrink: 0, objectFit: 'contain' }}
                        />
                        <ExtensionHeaderInfo
                            extension={extension}
                            headerTextColor={usesDefaultBg ? theme.palette.text.primary : headerTextColor}
                        />
                    </Box>
                </Box>
            </Container>
        </Box>
    );
};

export const ExtensionDetail: FunctionComponent = () => {
    const { namespace, name, target, '*': splat } = useParams();

    const navigate = useNavigate();
    const { pageSettings } = useContext(MainContext);

    const version = splat || undefined;
    const effectiveVersion = isTabSegment(version) ? undefined : version;
    const activeTab = parseTab(version);

    // Tab switches preserve scroll (see ScrollRestoration); when scrolled deep, glide
    // up so the new panel starts under the pinned pills.
    const bandRef = useRef<HTMLDivElement>(null);
    const prevTab = useRef(activeTab);
    useEffect(() => {
        if (prevTab.current === activeTab) return;
        prevTab.current = activeTab;
        const band = bandRef.current;
        if (!band) return;
        const pin = band.getBoundingClientRect().bottom + window.scrollY - NAVBAR_HEIGHT_PX;
        // Rest above the pin point
        const target = Math.max(0, pin);
        if (window.scrollY > target) {
            window.scrollTo({ top: target });
        }
    }, [activeTab]);

    // React Router v6 returns a possibly undefined type for params, but our route configuration guarantees these will be defined.
    const { loading, error, extension, reload } = useExtensionDetail(namespace!, name!, target!, effectiveVersion!);

    // Tab switches keep the scroll position (the effect above adjusts it).
    const goToTab = useCallback((path: string) => navigate(path, { state: { preserveScroll: true } }), [navigate]);

    const navigateToVersion = useCallback(
        (selectedVersion: string) => {
            if (!namespace || !name) return;
            navigate(
                selectedVersion === 'latest'
                    ? buildExtensionPath(namespace, name, target)
                    : buildExtensionPath(namespace, name, target, selectedVersion)
            );
        },
        [navigate, namespace, name, target]
    );

    // Computed before the early return so the shortcut hooks below run unconditionally.
    const basePath = namespace && name ? buildExtensionPath(namespace, name, target) : '';
    const changesPath = namespace && name ? buildExtensionPath(namespace, name, target, ExtensionTab.CHANGES) : '';
    const reviewsPath = namespace && name ? buildExtensionPath(namespace, name, target, ExtensionTab.REVIEWS) : '';

    useShortcut({
        key: 'o',
        label: 'Extension overview',
        order: 40,
        callback: () => goToTab(basePath),
        enabled: !!basePath
    });
    useShortcut({
        key: 'c',
        label: 'Extension changelog',
        order: 50,
        callback: () => goToTab(changesPath),
        enabled: !!basePath
    });
    useShortcut({
        key: 'r',
        label: 'Extension reviews',
        order: 60,
        callback: () => goToTab(reviewsPath),
        enabled: !!basePath
    });

    if (!namespace || !name) return null;

    let overviewPath = basePath;
    if (version && !isTabSegment(version)) {
        overviewPath = buildExtensionPath(namespace, name, target, version);
    } else if (extension && !extension.versionAlias.includes('latest')) {
        overviewPath = buildExtensionPath(namespace, name, target, extension.version);
    }

    const HeadTags = pageSettings.elements.extensionHeadTags;

    return (
        <>
            {HeadTags && <HeadTags extension={extension} pageSettings={pageSettings} />}
            <DelayedLoadIndicator loading={loading} />
            {extension && (
                <>
                    <ExtensionHeader extension={extension} bandRef={bandRef} />
                    <PageContainer flushTop>
                        {/* From md up a sliver of gutter remains so the scroller doesn't
                            clip the end pills' focus outline. */}
                        <PillTabs value={activeTab} gutters={{ xs: '1rem', sm: '1.5rem', md: '0.375rem' }}>
                            <PillTab
                                value={ExtensionTab.OVERVIEW}
                                label={
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.4375rem' }}>
                                        Overview<KbdKey>o</KbdKey>
                                    </Box>
                                }
                                component={RouteLink}
                                to={overviewPath}
                                state={{ preserveScroll: true }}
                            />
                            <PillTab
                                value={ExtensionTab.CHANGES}
                                label={
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.4375rem' }}>
                                        Changes<KbdKey>c</KbdKey>
                                    </Box>
                                }
                                component={RouteLink}
                                to={changesPath}
                                state={{ preserveScroll: true }}
                            />
                            <PillTab
                                value={ExtensionTab.REVIEWS}
                                label={
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.4375rem' }}>
                                        Ratings &amp; Reviews<KbdKey>r</KbdKey>
                                    </Box>
                                }
                                component={RouteLink}
                                to={reviewsPath}
                                state={{ preserveScroll: true }}
                            />
                        </PillTabs>
                        <Box sx={{ pt: 2 }}>
                            <Routes>
                                <Route
                                    path={ExtensionTab.REVIEWS}
                                    element={<ExtensionDetailReviews extension={extension} reviewsDidUpdate={reload} />}
                                />
                                <Route
                                    path={ExtensionTab.CHANGES}
                                    element={<ExtensionDetailChanges extension={extension} />}
                                />
                                <Route
                                    path='*'
                                    element={
                                        <ExtensionDetailOverview
                                            extension={extension}
                                            selectVersion={navigateToVersion}
                                        />
                                    }
                                />
                            </Routes>
                        </Box>
                    </PageContainer>
                </>
            )}
            {error && (
                <Box p={4}>
                    <Typography variant='h5'>{error.message}</Typography>
                </Box>
            )}
        </>
    );
};
