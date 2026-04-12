/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, Suspense, lazy } from 'react';
import { Helmet } from 'react-helmet-async';
import { styled, Theme } from '@mui/material/styles';
import { Link, Typography, Box } from '@mui/material';
import { Link as RouteLink, Route, useParams } from 'react-router-dom';
import GitHubIcon from '@mui/icons-material/GitHub';
import { Extension, NamespaceDetails } from '../extension-registry-types';
import { PageSettings } from '../page-settings';
import { ExtensionListRoutes } from '../pages/extension-list/extension-list-routes';
import { DefaultMenuContent, MobileMenuContent } from './menu-content';
import OpenVSXLogo from './openvsx-registry-logo';
import About from './about';
import { createAbsoluteURL } from '../utils';

export default function createPageSettings(prefersDarkMode: boolean, serverUrl: string, serverVersionPromise: Promise<string>): PageSettings {
    const toolbarContent: FunctionComponent = () =>
        <RouteLink to={ExtensionListRoutes.MAIN} aria-label={`Home - Open VSX Registry`}>
            <OpenVSXLogo width='auto' height='40px' marginTop='8px' prefersDarkMode={prefersDarkMode} />
        </RouteLink>;

    const link = ({ theme }: { theme: Theme }) => ({
        color: theme.palette.text.primary,
        textDecoration: 'none',
        '&:hover': {
            color: theme.palette.secondary.main,
            textDecoration: 'none'
        }
    });

    const StyledRouteLink = styled(RouteLink)(link);

    const ServerVersion = lazy(async () => {
        const version = await serverVersionPromise;
        return { default: () => <Typography variant='body2' sx={{ fontSize: '0.8rem' }}>Server Version: {version}</Typography> };
    });


    const footerContent: FunctionComponent<{ expanded: boolean }> = () =>
        <Box sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
        }}>
            <Link
                target='_blank'
                href='https://github.com/eclipse/openvsx'
                sx={(theme: Theme) => ({
                    ...link({ theme }),
                    display: 'flex',
                    alignItems: 'center',
                    fontSize: '1.1rem',
                    mb: { xs: 1, sm: 1, md: 0, lg: 0, xl: 0 }
                })}
            >
                <GitHubIcon />&nbsp;eclipse/openvsx
            </Link>
            <Suspense fallback={<div>Loading version...</div>}>
                <ServerVersion/>
            </Suspense>
            <StyledRouteLink to='/about'>
                About This Service
            </StyledRouteLink>
        </Box>;

    const searchHeader: FunctionComponent = () =>
        <Typography variant='h4' sx={{ mb: 2, fontWeight: 'fontWeightLight', letterSpacing: 4, textAlign: 'center' }}>
            Extensions for VS Code Compatible Editors
        </Typography>;

    const additionalRoutes: ReactNode = <Route path='/about' element={<About />} />;

    const headTags: FunctionComponent<{title: string}> = (props) => {
        return <Helmet>
            <title>{props.title}</title>
        </Helmet>;
    };

    const mainHeadTags: FunctionComponent<{pageSettings: PageSettings}> = (props) => {
        return headTags({ title: props.pageSettings.pageTitle });
    };

    const extensionHeadTags: FunctionComponent<{extension?: Extension, pageSettings: PageSettings}> = (props) => {
        const params = useParams();
        const name = props.extension
            ? props.extension.displayName ?? props.extension.name
            : params.name;

        return headTags({ title: `${name} – ${props.pageSettings.pageTitle}` });
    };

    const namespaceHeadTags: FunctionComponent<{namespaceDetails?: NamespaceDetails, name: string, pageSettings: PageSettings}> = (props) => {
        const name = props.namespaceDetails
            ? props.namespaceDetails.displayName ?? props.namespaceDetails.name
            : props.name;

        return headTags({ title: `${name} – ${props.pageSettings.pageTitle}` });
    };

    return {
        pageTitle: 'Open VSX Registry',
        themeType: prefersDarkMode ? 'dark' : 'light',
        publisherAgreement: {
            name: 'Open VSX'
        },
        elements: {
            toolbarContent,
            defaultMenuContent: DefaultMenuContent,
            mobileMenuContent: MobileMenuContent,
            footer: {
                content: footerContent,
                props: {
                    footerHeight: 69 // Maximal height reached for small screens
                }
            },
            searchHeader,
            additionalRoutes,
            mainHeadTags,
            extensionHeadTags,
            namespaceHeadTags
        },
        urls: {
            extensionDefaultIcon: '/default-icon.png',
            namespaceAccessInfo: 'https://github.com/eclipse/openvsx/wiki/Namespace-Access',
            publisherAgreement: createAbsoluteURL([serverUrl, 'documents', 'publisher-agreement.md'])
        }
    };
}
