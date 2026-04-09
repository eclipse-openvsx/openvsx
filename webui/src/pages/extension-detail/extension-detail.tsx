/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useCallback, useContext } from 'react';
import {
    Typography, Box, Container, Link, Avatar, Paper, Badge, Tabs, Tab, Stack, useTheme, PaletteMode,
    decomposeColor
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { Link as RouteLink, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import SaveAltIcon from '@mui/icons-material/SaveAlt';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import WarningIcon from '@mui/icons-material/Warning';
import { MainContext } from '../../context';
import { createRoute } from '../../utils';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { HoverPopover } from '../../components/hover-popover';
import { Extension, UserData } from '../../extension-registry-types';
import { TextDivider } from '../../components/text-divider';
import { ExtensionRatingStars } from './extension-rating-stars';
import { NamespaceDetailRoutes } from '../namespace-detail/namespace-detail-routes';
import { ExtensionDetailOverview } from './extension-detail-overview';
import { ExtensionDetailChanges } from './extension-detail-changes';
import { ExtensionDetailReviews } from './extension-detail-reviews';

export { ExtensionDetailRoutes } from './extension-detail-routes';
import { ExtensionDetailRoutes } from './extension-detail-routes';
import { useExtensionDetail } from './use-extension-details';

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
    REVIEWS = 'reviews',
}

const TAB_VALUES = new Set<string>(Object.values(ExtensionTab));

const isTabSegment = (segment?: string): segment is ExtensionTab =>
    TAB_VALUES.has(segment ?? '');

const parseTab = (segment?: string): ExtensionTab =>
    isTabSegment(segment) ? segment : ExtensionTab.OVERVIEW;

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
            }}
        >
            <WarningIcon fontSize='large' />
            <Box ml={1}>
                This version of the &ldquo;{extension.displayName ?? extension.name}&rdquo; extension was published
                by <Link href={extension.publishedBy.homepage}>
                    {extension.publishedBy.loginName}
                </Link>. That user account is not a verified publisher of
                the namespace &ldquo;{extension.namespace}&rdquo; of
                this extension. <Link href={pageSettings.urls.namespaceAccessInfo} target='_blank'>
                    See the documentation
                </Link> to learn how we handle namespaces and what you can do to eliminate this warning.
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
                {user.avatarUrl
                    ? <>{user.loginName}&nbsp;<Avatar src={user.avatarUrl} alt={user.loginName} sx={{ width: '20px', height: '20px' }} /></>
                    : user.loginName}
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
            <StyledLink href={extension.files.license} sx={{ color }} title={extension.license ? 'License type' : undefined}>
                {extension.license || 'Provided license'}
            </StyledLink>
        );
    }
    return <>{extension.license || 'Unlicensed'}</>;
};

const compactNumber = new Intl.NumberFormat(undefined, { notation: 'compact', compactDisplay: 'short' } as Intl.NumberFormatOptions);

const ExtensionHeaderInfo: FunctionComponent<{
    extension: Extension;
    headerTextColor: string;
}> = ({ extension, headerTextColor }) => {
    const downloadCountFormatted = compactNumber.format(extension.downloadCount || 0);
    const reviewCountFormatted = compactNumber.format(extension.reviewCount || 0);

    return (
        <Box overflow='auto' sx={{ pt: 1, overflow: 'visible' }}>
            <PreviewBadge color='secondary' badgeContent='Preview' invisible={!extension.preview}>
                <Typography variant='h5' sx={{ fontWeight: 'bold', mb: 1 }}>
                    {extension.displayName ?? extension.name}
                </Typography>
            </PreviewBadge>

            {extension.deprecated && (
                <Stack direction='row' alignItems='center'>
                    <WarningIcon fontSize='small' />
                    <Typography>
                        This extension has been deprecated.
                        {extension.replacement && (
                            <>&nbsp;Use <StyledLink sx={{ color: headerTextColor }} href={extension.replacement.url}>
                                {extension.replacement.displayName}
                            </StyledLink> instead.</>
                        )}
                    </Typography>
                </Stack>
            )}

            <Box sx={{ display: 'flex', alignItems: 'center', color: headerTextColor, flexDirection: { xs: 'column', md: 'row' } }}>
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                    <VerificationIcon verified={extension.verified} color={headerTextColor} />&nbsp;
                    <StyledRouteLink to={createRoute([NamespaceDetailRoutes.ROOT, extension.namespace])} style={{ color: headerTextColor }}>
                        {extension.namespaceDisplayName}
                    </StyledRouteLink>
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall />
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                    Published by&nbsp;<UserPopover user={extension.publishedBy} color={headerTextColor} />
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall />
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                    <LicenseLink extension={extension} color={headerTextColor} />
                </Box>
            </Box>

            <Box mt={2} mb={2} overflow='auto'>
                <Typography sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{extension.description}</Typography>
            </Box>

            <Box sx={{ display: 'flex', alignItems: 'center', color: headerTextColor, justifyContent: { xs: 'center', md: 'flex-start' } }}>
                <Box component='span' sx={{ display: 'flex', alignItems: 'center' }}
                    title={extension.downloadCount && extension.downloadCount >= 1000 ? `${extension.downloadCount} downloads` : undefined}
                >
                    <SaveAltIcon fontSize='small' />&nbsp;{downloadCountFormatted}&nbsp;{extension.downloadCount === 1 ? 'download' : 'downloads'}
                </Box>
                <TextDivider backgroundColor={headerTextColor} />
                <StyledLink
                    href={createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name, 'reviews'])}
                    sx={{ display: 'flex', alignItems: 'center', color: headerTextColor }}
                    title={
                        extension.averageRating === undefined
                            ? 'Not rated yet'
                            : `Average rating: ${Math.round(extension.averageRating * 10) / 10} out of 5 (${extension.reviewCount} reviews)`
                    }
                >
                    <ExtensionRatingStars number={extension.averageRating ?? 0} fontSize='small' />
                    ({reviewCountFormatted})
                </StyledLink>
            </Box>
        </Box>
    );
};

const ExtensionHeader: FunctionComponent<{
    extension: Extension;
    icon: string | undefined;
}> = ({ extension, icon }) => {
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

    return (
        <Box
            sx={{
                bgcolor: headerColor,
                color: headerTextColor,
                filter: extension.deprecated ? 'grayscale(100%)' : undefined
            }}
        >
            <Container maxWidth='xl'>
                <Box sx={{ display: 'flex', alignItems: 'center', flexDirection: 'column', py: 4, px: 0 }}>
                    <UnverifiedBanner extension={extension} headerTextColor={headerTextColor} themeType={themeType} />
                    <Box
                        sx={{
                            display: 'flex',
                            width: '100%',
                            flexDirection: { xs: 'column', md: 'row' },
                            textAlign: { xs: 'center', md: 'start' },
                            alignItems: { xs: 'center', md: 'normal' }
                        }}
                    >
                        <Box
                            component='img'
                            src={icon ?? pageSettings.urls.extensionDefaultIcon}
                            alt={extension.displayName ?? extension.name}
                            sx={{ height: '7.5rem', maxWidth: '9rem', mr: { xs: 0, md: '2rem' }, pt: 1 }}
                        />
                        <ExtensionHeaderInfo extension={extension} headerTextColor={headerTextColor} />
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

    // React Router v6 returns a possibly undefined type for params, but our route configuration guarantees these will be defined.
    const { loading, error, extension, icon, reload } = useExtensionDetail(namespace!, name!, target!, effectiveVersion!);

    const navigateToVersion = useCallback((selectedVersion: string) => {
        if (!namespace || !name) return;
        navigate(selectedVersion === 'latest'
            ? buildExtensionPath(namespace, name, target)
            : buildExtensionPath(namespace, name, target, selectedVersion));
    }, [navigate, namespace, name, target]);

    if (!namespace || !name) return null;

    const basePath = buildExtensionPath(namespace, name, target);
    const reviewsPath = buildExtensionPath(namespace, name, target, ExtensionTab.REVIEWS);
    const changesPath = buildExtensionPath(namespace, name, target, ExtensionTab.CHANGES);

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
                    <ExtensionHeader extension={extension} icon={icon} />
                    <Container maxWidth='xl'>
                        <Tabs value={activeTab} indicatorColor='secondary'>
                            <Tab value={ExtensionTab.OVERVIEW} label='Overview' component={RouteLink} to={overviewPath} />
                            <Tab value={ExtensionTab.CHANGES} label='Changes' component={RouteLink} to={changesPath} />
                            <Tab value={ExtensionTab.REVIEWS} label='Ratings &amp; Reviews' component={RouteLink} to={reviewsPath} />
                        </Tabs>
                        <Routes>
                            <Route path={ExtensionTab.REVIEWS} element={<ExtensionDetailReviews extension={extension} reviewsDidUpdate={reload} />} />
                            <Route path={ExtensionTab.CHANGES} element={<ExtensionDetailChanges extension={extension} />} />
                            <Route path='*' element={<ExtensionDetailOverview extension={extension} selectVersion={navigateToVersion} />} />
                        </Routes>
                    </Container>
                </>
            )}
            {error && <Box p={4}><Typography variant='h5'>{error.message}</Typography></Box>}
        </>
    );
};
