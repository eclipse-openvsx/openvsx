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

import { FunctionComponent, lazy, Suspense, useContext, useEffect, useState } from 'react';
import { Routes, Route, useNavigate } from 'react-router';
import { Box } from '@mui/material';
import { styled } from '@mui/material/styles';
import { Banner } from '../components/banner';
import { ShortcutsModal } from '../components/shortcuts-modal';
import { MainContext } from '../context';
import { useSearch } from '../hooks/use-search';
import { NavbarChromeProvider } from '../context/navbar-chrome-context';
import { useShortcut } from '../hooks/use-shortcut';
import { getCookieValueByKey, setCookie } from '../utils';
import { ExtensionListRoutes } from '../pages/extension-list/extension-list-routes';
import { UserSettingsRoutes } from '../pages/user/user-settings-routes';
import { NamespaceDetailRoutes } from '../pages/namespace-detail/namespace-detail-routes';
import { PublishRoutes } from '../pages/publish/publish-routes';
import { PublishPage } from '../pages/publish/publish-page';
import { ExtensionDetailRoutes } from '../pages/extension-detail/extension-detail-routes';
import { ExtensionDetail } from '../pages/extension-detail/extension-detail';
import { HomePage } from '../pages/home/home-page';
import { SearchPage } from '../pages/search/search-page';
import { NamespaceDetail } from '../pages/namespace-detail/namespace-detail';
import { NotFound } from '../not-found';
import { NAVBAR_HEIGHT } from '../default/theme';
import { AppNavbar } from './app-navbar';
import { AppFooter } from './app-footer';
import { ScrollRestoration } from './scroll-restoration';

const UserSettings = lazy(() => import('../pages/user/user-settings').then(m => ({ default: m.UserSettings })));

const Wrapper = styled(Box)({
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
    minHeight: '100vh'
});

const AppLayoutContent: FunctionComponent<AppLayoutProps> = props => {
    const { pageSettings } = useContext(MainContext);
    const { additionalRoutes: AdditionalRoutes, banner: BannerComponent, footer } = pageSettings.elements;
    // The legacy footer is fixed to the viewport bottom; pad the content to stay clear of it.
    const legacyFooterHeight = typeof footer === 'object' && 'content' in footer ? footer.props?.footerHeight : 0;

    const navigate = useNavigate();
    const { search } = useSearch();
    const [isBannerOpen, setIsBannerOpen] = useState(false);
    const [shortcutsOpen, setShortcutsOpen] = useState(false);

    useEffect(() => {
        const banner = pageSettings.elements.banner;
        if (!banner) return;
        if (banner.cookie && getCookieValueByKey(banner.cookie.key) === banner.cookie.value) return;
        // Start collapsed on load, then slide open a beat later for a gentle entrance.
        const timer = setTimeout(() => setIsBannerOpen(true), 600);
        return () => clearTimeout(timer);
    }, []);

    // Global navigation shortcuts with no on-screen affordance. Shortcuts tied to a
    // visible control (?, d, p) are registered where that control renders, so a
    // customized footer/menu brings its own instead of inheriting ours.
    useShortcut({
        key: 'h',
        label: 'Go to home',
        order: 4,
        callback: () => navigate('/')
    });
    useShortcut({
        key: 's',
        label: 'Go to search',
        order: 5,
        // Through `search` (not a bare navigate) so the field clears with the URL.
        callback: () => search({ query: '' })
    });

    const onDismissBannerButtonClick = () => {
        const onClose = pageSettings.elements.banner?.props?.onClose;
        if (onClose) onClose();
        const cookie = pageSettings.elements.banner?.cookie;
        if (cookie) setCookie(cookie);
        setIsBannerOpen(false);
    };

    return (
        <Wrapper>
            <ScrollRestoration />
            {BannerComponent ? (
                <Banner
                    open={isBannerOpen}
                    showDismissButton={BannerComponent.props?.dismissButton?.show}
                    dismissButtonLabel={BannerComponent.props?.dismissButton?.label}
                    dismissButtonOnClick={onDismissBannerButtonClick}
                    color={BannerComponent.props?.color}>
                    <BannerComponent.content />
                </Banner>
            ) : null}
            <AppNavbar />
            {/* Mobile: fill the viewport so the (screen-tall) footer stays below the fold.
                Desktop: flexGrow alone sticks the footer to the viewport bottom. A flex
                column so pages can opt into stretching over the leftover space
                (e.g. the search page runs its sidebar seam down to the footer). */}
            <Box
                sx={{
                    flexGrow: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: { xs: `calc(100vh - ${NAVBAR_HEIGHT})`, sm: 0 },
                    // Only the fixed legacy footer needs clearance; the in-flow footer's gap lives on <PageContainer>.
                    pb: legacyFooterHeight ? `${legacyFooterHeight + 24}px` : 0
                }}>
                <Suspense fallback={null}>
                    <Routes>
                        <Route path={ExtensionListRoutes.MAIN} element={<HomePage />} />
                        <Route path={ExtensionListRoutes.SEARCH} element={<SearchPage />} />
                        <Route
                            path={UserSettingsRoutes.MAIN}
                            element={<UserSettings userLoading={props.userLoading} />}
                        />
                        <Route
                            path={UserSettingsRoutes.EXTENSION_SETTINGS}
                            element={<UserSettings userLoading={props.userLoading} />}
                        />
                        <Route
                            path={UserSettingsRoutes.NAMESPACE_DETAIL}
                            element={<UserSettings userLoading={props.userLoading} />}
                        />
                        <Route path={PublishRoutes.ROOT} element={<PublishPage />} />
                        <Route path={NamespaceDetailRoutes.MAIN} element={<NamespaceDetail />} />
                        <Route path={ExtensionDetailRoutes.MAIN} element={<ExtensionDetail />} />
                        <Route path={ExtensionDetailRoutes.MAIN_TARGET} element={<ExtensionDetail />} />
                        {AdditionalRoutes ?? null}
                        <Route path='*' element={<NotFound />} />
                    </Routes>
                </Suspense>
            </Box>
            <AppFooter onOpenShortcuts={() => setShortcutsOpen(true)} />
            <ShortcutsModal open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />
        </Wrapper>
    );
};

// Keyboard shortcuts and search now live app-wide in AppProviders; this keeps
// only the navbar-chrome channel the pages below declare into.
export const AppLayout: FunctionComponent<AppLayoutProps> = props => (
    <NavbarChromeProvider>
        <AppLayoutContent {...props} />
    </NavbarChromeProvider>
);

export interface AppLayoutProps {
    userLoading: boolean;
}
