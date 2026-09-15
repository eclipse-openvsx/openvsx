/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ComponentType, ReactNode } from 'react';
import { SxProps, Theme } from '@mui/material/styles';
import { Extension, NamespaceDetails, SortBy } from './extension-registry-types';
import { AdminPage } from './pages/admin-dashboard/nav-types';
import { Cookie } from './utils';

/** One entry contributed to the account menu, rendered by the menu's own styling. */
export interface UserMenuEntryProps {
    /** Route the entry navigates to; the menu closes on click. */
    to: string;
    /** Icon component (e.g. a MUI icon), sized and coloured by the menu. */
    icon: ComponentType<{ sx?: SxProps<Theme> }>;
    /** The entry's label. */
    children: ReactNode;
}

export interface UserMenuContentProps {
    /** Dismisses the menu. `MenuEntry` already does this on click. */
    close: () => void;
    /** Renders one entry styled to match the menu it appears in. */
    MenuEntry: ComponentType<UserMenuEntryProps>;
}

export interface FooterLink {
    label: ReactNode;
    href: string;
    /** Open in a new tab. */
    external?: boolean;
}

export interface FooterColumn {
    heading: ReactNode;
    links: FooterLink[];
}

export interface FooterSocialLink {
    /** Accessible label / tooltip for the icon button. */
    title: string;
    href: string;
    icon: ReactNode;
}

/**
 * Structured, data-driven footer rendered by the library chrome (brand, link
 * columns and social icons). Every field is consumer-provided branding and may
 * be a plain string or any React node.
 */
export interface StructuredFooterSettings {
    brand?: {
        logo?: ReactNode;
        name: ReactNode;
        description?: ReactNode;
    };
    columns?: FooterColumn[];
    social?: FooterSocialLink[];
    copyright?: ReactNode;
    showVersion?: boolean;
    /** Extra node appended to the footer's bottom bar, next to the built-in controls. */
    extra?: ReactNode;
}

/**
 * Legacy footer: a fully custom component fixed to the bottom of the viewport,
 * receiving an `expanded` flag that is true while hovered. Kept for backward
 * compatibility with consumers written before {@link StructuredFooterSettings}.
 */
export interface CustomFooterSettings {
    content: ComponentType<{ expanded: boolean }>;
    props?: {
        /** Collapsed footer height; the page content is bottom-padded by it to stay clear of the fixed footer. */
        footerHeight?: number;
    };
}

/**
 * Footer configuration. Provide {@link StructuredFooterSettings} to feed the
 * built-in footer chrome, a component to replace the footer entirely (rendered
 * bare at the bottom of the page), or {@link CustomFooterSettings} (with
 * `content`) for the legacy hover-expanding chrome.
 */
export type FooterSettings = ComponentType | CustomFooterSettings | StructuredFooterSettings;

/** A curated row of extensions on the home page, fetched with the given ordering. */
export interface HomeCuratedSection {
    title: string;
    subtitle: string;
    sortBy: SortBy;
}

export interface HomeInvolvementCard {
    icon: ReactNode;
    title: string;
    description: string;
    href: string;
    label: string;
}

/** Consumer-provided content for the built-in home page (branding and curated data). */
export interface HomeSettings {
    popularSearches?: string[];
    curatedSections?: HomeCuratedSection[];
    involvement?: {
        heading?: string;
        cards: HomeInvolvementCard[];
    };
}

/**
 * Home route configuration. Provide {@link HomeSettings} to feed the built-in
 * home page, or a component to replace it entirely. The built-in sections are
 * exported so a replacement can be composed from them; each boxes itself, so
 * don't wrap them in another width-constraining container.
 */
export type HomePageSettings = HomeSettings | ComponentType;

export interface PageSettings {
    pageTitle: string;
    themeType?: 'light' | 'dark';
    publisherAgreement?: {
        name?: string;
        email?: string;
    };
    elements: {
        toolbarContent?: ComponentType;
        defaultMenuContent?: ComponentType;
        mobileMenuContent?: ComponentType;
        /**
         * Extra entries for the logged-in account menu, rendered above the admin entry. The same
         * component is used by the desktop and the mobile menu, which style their entries
         * differently, so build entries with the supplied {@link UserMenuContentProps.MenuEntry}
         * rather than a `MenuItem` of your own.
         */
        userMenuContent?: ComponentType<UserMenuContentProps>;
        footer?: FooterSettings;
        home?: HomePageSettings;
        searchHeader?: ComponentType;
        reportAbuse?: ComponentType<{ extension: Extension; sx?: SxProps<Theme> }>;
        claimNamespace?: ComponentType<{ namespace: string; extension?: Extension; sx?: SxProps<Theme> }>;
        downloadTerms?: ComponentType;
        additionalRoutes?: ReactNode;
        /**
         * Extra pages for the admin dashboard, each appearing in the side panel, as a card on the
         * dashboard overview and as a route. Additive only — see {@link AdminPage}.
         */
        adminPages?: AdminPage[];
        banner?: {
            content: ComponentType;
            props?: {
                dismissButton?: {
                    show?: boolean;
                    label?: string;
                };
                onClose?: () => void;
                color?: 'info' | 'warning';
            };
            cookie?: Cookie;
        };
        mainHeadTags?: ComponentType<{ pageSettings: PageSettings }>;
        extensionHeadTags?: ComponentType<{ extension?: Extension; pageSettings: PageSettings }>;
        namespaceHeadTags?: ComponentType<{
            namespaceDetails?: NamespaceDetails;
            name: string;
            pageSettings: PageSettings;
        }>;
    };
    urls: {
        extensionDefaultIcon: string;
        namespaceAccessInfo: string;
        publisherAgreement?: string;
        trustedPublishing?: string;
    };
}
