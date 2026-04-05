/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, FunctionComponent, ReactElement, ReactNode, useContext, useEffect, useState, useRef } from 'react';
import {
    Typography, Box, Theme, Container, Link, Avatar, Paper, Badge, SxProps, Tabs, Tab, Stack, useTheme, PaletteMode,
    decomposeColor
} from '@mui/material';
import { Link as RouteLink, useNavigate, useParams } from 'react-router-dom';
import SaveAltIcon from '@mui/icons-material/SaveAlt';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import WarningIcon from '@mui/icons-material/Warning';
import { MainContext } from '../../context';
import { createRoute } from '../../utils';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { HoverPopover } from '../../components/hover-popover';
import { Extension, UserData, isError } from '../../extension-registry-types';
import { TextDivider } from '../../components/text-divider';
import { ExtensionRatingStars } from './extension-rating-stars';
import { NamespaceDetailRoutes } from '../namespace-detail/namespace-detail-routes';
import { ExtensionDetailOverview } from './extension-detail-overview';
import { ExtensionDetailChanges } from './extension-detail-changes';
import { ExtensionDetailReviews } from './extension-detail-reviews';
import { ExtensionDetailRoutes } from './extension-detail-routes';
import styled from '@mui/material/styles/styled';

const alignVertically = {
    display: 'flex',
    alignItems: 'center'
};

const link = {
    display: 'contents',
    cursor: 'pointer',
    textDecoration: 'none',
    '&:hover': {
        textDecoration: 'underline'
    }
};

const StyledRouteLink = styled(RouteLink)(link);
const StyledLink = styled(Link)(link);
const StyledHoverPopover = styled(HoverPopover)(alignVertically);

export const ExtensionDetail: FunctionComponent = () => {
    const theme = useTheme();
    const [loading, setLoading] = useState<boolean>(true);
    const [notFoundError, setNotFoundError] = useState<string>();
    const [extension, setExtension] = useState<Extension>();
    const [icon, setIcon] = useState<string>();

    const navigate = useNavigate();
    const { namespace, name, target, version } = useParams();
    const { handleError, pageSettings, service } = useContext(MainContext);

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        updateExtension();
        return () => {
            abortController.current.abort();
            if (icon) {
                URL.revokeObjectURL(icon);
            }
        };
    }, []);

    useEffect(() => {
        if (versionPointsToTab(version)) {
            return;
        }

        setLoading(true);
        updateExtension();
    }, [namespace, name, target, version]);

    const updateExtension = async (): Promise<void> => {
        const extensionUrl = getExtensionApiUrl();
        try {
            const response = await service.getExtensionDetail(abortController.current, extensionUrl);
            if (isError(response)) {
                throw response;
            }
            const extension = response as Extension;
            const icon = await updateIcon(extension);
            setExtension(extension);
            setIcon(icon);
            setLoading(false);
        } catch (err) {
            if (err && err.status === 404) {
                setNotFoundError(`Extension Not Found: ${namespace}.${name}`);
                setLoading(false);
            } else {
                handleError(err);
            }
            setLoading(false);
        }
    };

    const getExtensionApiUrl = (): string => {
        return versionPointsToTab(version)
            ? service.getExtensionApiUrl({ namespace: namespace as string, name: name as string })
            : service.getExtensionApiUrl({ namespace: namespace as string, name: name as string, target: target, version: version });
    };

    const updateIcon = async (extension: Extension): Promise<string | undefined> => {
        if (icon) {
            URL.revokeObjectURL(icon);
        }

        return await service.getExtensionIcon(abortController.current, extension);
    };

    const onVersionSelect = (version: string): void => {
        const arr = [ExtensionDetailRoutes.ROOT, namespace as string, name as string];
        if (target) {
            arr.push(target);
        }
        if (version !== 'latest') {
            arr.push(version);
        }

        navigate(createRoute(arr));
    };

    const onReviewUpdate = (): void => {
        updateExtension();
    };

    const handleTabChange = (event: ChangeEvent, newTab: string): void => {
        const previousTab = versionPointsToTab(version) ? version : 'overview';
        if (newTab !== previousTab) {
            const arr = [ExtensionDetailRoutes.ROOT, namespace as string, name as string];
            if (target) {
                arr.push(target);
            }

            if (newTab === 'reviews' || newTab === 'changes') {
                arr.push(newTab);
            } else if (version && !versionPointsToTab(version)) {
                arr.push(version);
            } else if (extension && !isLatestVersion(extension)) {
                arr.push(extension.version);
            }

            navigate(createRoute(arr));
        }
    };

    const isLatestVersion = (extension: Extension): boolean => {
        return extension.versionAlias.indexOf('latest') >= 0;
    };

    const versionPointsToTab = (version?: string): boolean => {
        return version === 'reviews' || version === 'changes';
    };

    const renderHeaderTags = (extension?: Extension): ReactNode => {
        const { extensionHeadTags: ExtensionHeadTagsComponent } = pageSettings.elements;
        return <>
            { ExtensionHeadTagsComponent
                ? <ExtensionHeadTagsComponent extension={extension} pageSettings={pageSettings}/>
                : null
            }
        </>;
    };

    const renderNotFound = (): ReactNode => {
        return <>
            {
                notFoundError ?
                <Box p={4}>
                    <Typography variant='h5'>
                        {notFoundError}
                    </Typography>
                </Box>
                : null
            }
        </>;
    };

    const renderTab = (tab: string, extension: Extension): ReactNode => {
        switch (tab) {
            case 'changes':
                return <ExtensionDetailChanges extension={extension} />;
            case 'reviews':
                return <ExtensionDetailReviews extension={extension} reviewsDidUpdate={onReviewUpdate} />;
            default:
                return <ExtensionDetailOverview extension={extension} selectVersion={onVersionSelect} />;
        }
    };

    const renderExtension = (extension: Extension): ReactNode => {
        const tab = versionPointsToTab(version) ? version as string : 'overview';
        const themeType = (extension.galleryTheme || pageSettings.themeType) ?? 'light';
        const fallbackColor = theme.palette.neutral[themeType] as string;
        let headerColor = extension.galleryColor || fallbackColor;

        try {
            // check if the color string can be decomposed, i.e. if mui understands it, otherwise
            // fall back to the neutral color of the used palette.
            decomposeColor(headerColor);
        } catch (error) {
            headerColor = fallbackColor;
        }

        const headerTextColor = theme.palette.getContrastText(headerColor);

        return <>
            <Box
                sx={{
                    bgcolor: headerColor,
                    color: headerTextColor,
                    filter: extension.deprecated ? 'grayscale(100%)' : undefined
                }}
            >
                <Container maxWidth='xl'>
                    <Box sx={{ display: 'flex', alignItems: 'center', flexDirection: 'column', py: 4, px: 0 }}>
                        {renderBanner(extension, headerTextColor, themeType)}
                        <Box
                            sx={{
                                display: 'flex',
                                width: '100%',
                                flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' },
                                textAlign: { xs: 'center', sm: 'center', md: 'start', lg: 'start', xl: 'start' },
                                alignItems: { xs: 'center', sm: 'center', md: 'normal', lg: 'normal', xl: 'normal' }
                            }}
                        >
                            <Box
                                component='img'
                                src={icon ?? pageSettings.urls.extensionDefaultIcon }
                                alt={extension.displayName ?? extension.name}
                                sx={{
                                    height: '7.5rem',
                                    maxWidth: '9rem',
                                    mr: { xs: 0, sm: 0, md: '2rem', lg: '2rem', xl: '2rem' },
                                    pt: 1
                                }}
                            />
                            {renderHeaderInfo(extension, headerTextColor)}
                        </Box>
                    </Box>
                </Container>
            </Box>
            <Container maxWidth='xl'>
                <Box>
                    <Box>
                        <Tabs value={tab} onChange={handleTabChange} indicatorColor='secondary'>
                            <Tab value='overview' label='Overview' />
                            <Tab value='changes' label='Changes' />
                            <Tab value='reviews' label='Ratings &amp; Reviews' />
                        </Tabs>
                        {renderTab(tab, extension)}
                    </Box>
                </Box>
            </Container>
        </>;
    };

    const renderBanner = (extension: Extension, headerTextColor: string, themeType: PaletteMode): ReactNode => {
        if (!extension.verified) {
            return <Paper
                sx={{
                    display: 'flex',
                    maxWidth: '800px',
                    p: 2,
                    mt: 0,
                    mr: { xs: 0, sm: 0, md: 6, lg: 6, xl: 6 },
                    mb: { xs: 2, sm: 2, md: 4, lg: 4, xl: 4 },
                    ml: { xs: 0, sm: 0, md: 6, lg: 6, xl: 6 },
                    bgcolor: `warning.${themeType}`,
                    color: headerTextColor,
                    '& a': {
                        color: headerTextColor,
                        textDecoration: 'underline'
                    }
                }}
            >
                <WarningIcon fontSize='large' />
                <Box ml={1}>
                    This version of the &ldquo;{extension.displayName ?? extension.name}&rdquo; extension was published
                    by <Link href={extension.publishedBy.homepage}>
                        {extension.publishedBy.loginName}
                    </Link>. That user account is not a verified publisher of
                    the namespace &ldquo;{extension.namespace}&rdquo; of
                    this extension. <Link
                        href={pageSettings.urls.namespaceAccessInfo}
                        target='_blank' >
                        See the documentation
                    </Link> to learn how we handle namespaces and what you can do to eliminate this warning.
                </Box>
            </Paper>;
        }
        return null;
    };

    const renderHeaderInfo = (extension: Extension, headerTextColor: string): ReactNode => {
        const numberFormat = new Intl.NumberFormat(undefined, { notation: 'compact', compactDisplay: 'short' } as any);
        const downloadCountFormatted = numberFormat.format(extension.downloadCount || 0);
        const reviewCountFormatted = numberFormat.format(extension.reviewCount || 0);
        const previewBadgeStyle = (theme: Theme) => ({
            "& .MuiBadge-badge": {
                top: theme.spacing(1),
                right: theme.spacing(-5)
            }
        });

        return (
        <Box overflow='auto' sx={{ pt: 1, overflow: 'visible' }}>
            <Badge color='secondary' badgeContent='Preview' invisible={!extension.preview} sx={previewBadgeStyle}>
                <Typography variant='h5' sx={{ fontWeight: 'bold', mb: 1 }}>
                    { extension.displayName ?? extension.name}
                </Typography>
            </Badge>
            { extension.deprecated &&
                <Stack direction='row' alignItems='center'>
                    <WarningIcon fontSize='small' />
                    <Typography>
                        This extension has been deprecated.{extension.replacement && <>&nbsp;Use <StyledLink sx={{ color: headerTextColor }} href={extension.replacement.url}>
                            {extension.replacement.displayName}
                        </StyledLink> instead.</>}
                    </Typography>
                </Stack>
            }
            <Box
                sx={{
                    ...alignVertically,
                    color: headerTextColor,
                    flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
                }}
            >
                <Box sx={alignVertically}>
                    {renderAccessInfo(extension, headerTextColor)}&nbsp;
                    <StyledRouteLink
                        to={createRoute([NamespaceDetailRoutes.ROOT, extension.namespace])}
                        style={{ color: headerTextColor }}>
                        {extension.namespaceDisplayName}
                    </StyledRouteLink>
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall={true} />
                <Box sx={alignVertically}>
                    Published by&nbsp;{renderUser(extension.publishedBy, headerTextColor, alignVertically)}
                </Box>
                <TextDivider backgroundColor={headerTextColor} collapseSmall={true} />
                <Box sx={alignVertically}>
                    {renderLicense(extension, headerTextColor)}
                </Box>
            </Box>
            <Box mt={2} mb={2} overflow='auto'>
                <Typography sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{extension.description}</Typography>
            </Box>
            <Box
                sx={{
                    ...alignVertically,
                    color: headerTextColor,
                    justifyContent: { xs: 'center', sm: 'center', md: 'flex-start', lg: 'flex-start', xl: 'flex-start' }
                }}
            >
                <Box component='span' sx={alignVertically}
                    title={extension.downloadCount && extension.downloadCount >= 1000 ? `${extension.downloadCount} downloads` : undefined}>
                    <SaveAltIcon fontSize='small' />&nbsp;{downloadCountFormatted}&nbsp;{extension.downloadCount === 1 ? 'download' : 'downloads'}
                </Box>
                <TextDivider backgroundColor={headerTextColor} />
                <StyledLink
                    href={createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name, 'reviews'])}
                    sx={{
                        ...alignVertically,
                        color: headerTextColor
                    }}
                    title={
                        extension.averageRating !== undefined ?
                            `Average rating: ${getRoundedRating(extension.averageRating)} out of 5 (${extension.reviewCount} reviews)`
                            : 'Not rated yet'
                    }>
                    <ExtensionRatingStars number={extension.averageRating ?? 0} fontSize='small' />
                    ({reviewCountFormatted})
                </StyledLink>
                </Box>
            </Box>
        );
    };

    const getRoundedRating = (rating: number): number => {
        return Math.round(rating * 10) / 10;
    };

    const renderAccessInfo = (extension: Extension, themeColor: string): ReactNode => {
        let icon: ReactElement;
        let title: string;
        if (extension.verified) {
            icon = <VerifiedUserIcon fontSize='small' />;
            title = 'Verified publisher';
        } else {
            icon = <WarningIcon fontSize='small' />;
            title = 'Unverified publisher';
        }
        return <StyledLink
            href={pageSettings.urls.namespaceAccessInfo}
            target='_blank'
            title={title}
            sx={{ color: themeColor }}>
            {icon}
        </StyledLink>;
    };

    const renderUser = (user: UserData, themeColor: string, alignVertically: SxProps<Theme>): ReactNode => {
        const popupContent = <Box display='flex' flexDirection='row'>
            {
                user.avatarUrl ?
                <Avatar
                    src={user.avatarUrl}
                    alt={user.fullName ?? user.loginName}
                    variant='rounded'
                    sx={{ width: '60px', height: '60px' }} />
                : null
            }
            <Box ml={2}>
                {
                    user.fullName ?
                    <Typography variant='h6'>{user.fullName}</Typography>
                    : null
                }
                <Typography variant='body1'>{user.loginName}</Typography>
            </Box>
        </Box>;
        return <StyledHoverPopover
            id={`user_${user.loginName}_popover`}
            popupContent={popupContent}
        >
            <StyledLink href={user.homepage} sx={{ color: themeColor }}>
                {
                    user.avatarUrl ?
                    <>
                        {user.loginName}&nbsp;<Avatar
                            src={user.avatarUrl}
                            alt={user.loginName}
                            sx={{ width: '20px', height: '20px' }} />
                    </>
                    : user.loginName
                }
            </StyledLink>
        </StyledHoverPopover>;
    };

    const renderLicense = (extension: Extension, themeColor: string): ReactNode => {
        if (extension.files.license) {
            return <StyledLink
                href={extension.files.license}
                sx={{ color: themeColor }}
                title={extension.license ? 'License type' : undefined} >
                {extension.license || 'Provided license'}
            </StyledLink>;
        } else if (extension.license) {
            return extension.license;
        } else {
            return 'Unlicensed';
        }
    };

    return <>
        { renderHeaderTags(extension) }
        <DelayedLoadIndicator loading={loading} />
        {
            extension
                ? renderExtension(extension)
                : renderNotFound()
        }
    </>;
};