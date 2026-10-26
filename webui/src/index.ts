/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

// Named exports keep the published surface deliberate; `export *` is reserved for
// modules that are public as a whole.

// The app and the context it supplies, for custom pages and consumer test harnesses.
export { Main, type MainProps } from './main';
export * from './page-settings';
export { AppProviders } from './app-providers';
export { MainContext } from './context';
export { useRegistryValue } from './hooks/use-registry-value';
export { useReportedQuery } from './hooks/use-reported-query';

// The registry client, plus the request layer for building a service around endpoints it doesn't cover.
export * from './extension-registry-service';
export * from './extension-registry-types';
export { sendRequest, isNotFound, type ErrorResponse } from './server-request';
export { createRoute, createAbsoluteURL, addQuery } from './utils';

// So a deployment with its own menu content can gate an admin entry the way the built-in menu does.
export { hasPermission, hasAnyAdminAccess } from './permissions';

// Built-in pages, and the shape of the pages contributed through `PageSettings.elements.adminPages`.
export { ExtensionDetail } from './pages/extension-detail/extension-detail';
export { ExtensionDetailRoutes } from './pages/extension-detail/extension-detail-routes';
export { NamespaceDetail } from './pages/namespace-detail/namespace-detail';
export { UserSettings, type UserSettingsProps } from './pages/user/user-settings';
export { NotFound } from './not-found';
export type { AdminPage, AdminPageCategory } from './pages/admin-dashboard/nav-types';
export type { AdminSettingsSection, AdminSettingsFlag } from './pages/admin-dashboard/settings-sections';

// Building blocks of the built-in home page, for composing a custom one.
export { HeroSearch, type HeroSearchProps } from './pages/home/hero-search';
export { BrowseCategories, type BrowseCategoriesProps } from './pages/home/browse-categories';
export { CuratedSections, type CuratedSectionsProps } from './pages/home/curated-sections';
export { GetInvolved, type GetInvolvedProps } from './pages/home/get-involved';
export {
    useHomeCategories,
    useCuratedRows,
    type CuratedRow,
    DEFAULT_CURATED_SECTIONS
} from './pages/home/use-home-data';

export { ExtensionList, type ExtensionListProps } from './components/extension-list';
export { ExtensionCard, type ExtensionCardProps } from './components/extension-card';
export { CategoryPill, type CategoryPillProps } from './components/category-pill';
export { useCategories, CATEGORY_ICONS, DefaultCategoryIcon } from './components/categories';
export { Pill } from './components/pill';
export { KbdKey } from './components/kbd-key';
export { OpenVsxMark } from './components/openvsx-mark';
export { PageContainer, type PageContainerProps } from './components/page-container';
export { ListLoadError, type ListLoadErrorProps } from './components/list-load-error';
// Also the app's drop target for .vsix packages, so a custom menu keeps drag-and-drop publishing.
export { PublishButton, type PublishButtonProps } from './components/publish/publish-button';

// `RegistryBanner` is the default for `PageSettings.elements.banner`; the rest lets a deployment
// render the same admin-configured settings its own way.
export { RegistryBanner } from './components/registry-banner';
export { useSiteSettings, siteSettingsQueryKey } from './components/use-site-settings';
export { Banner, type BannerProps } from './components/banner';
export { SanitizedMarkdown, type SanitizedMarkdownProps } from './components/sanitized-markdown';

// Theme tokens and styling primitives shared with the library chrome, so custom pages line up with it.
export { default as createDefaultTheme, MONO_FONT, NAVBAR_HEIGHT, NAVBAR_HEIGHT_PX } from './default/theme';
export * from './components/page-primitives';
// Formats numbers and dates the way the built-in pages show them.
export { formatCompactNumber, formatRating, toRelativeTime } from './utils';

export { useSearch, type SearchFilter, type SearchOptions, type SearchAction } from './hooks/use-search';
export { useSearchBar, usePageSearchBar, type PageSearchBarValue } from './context/search/page-search-bar-context';
export { useSearchFocus, type ResultsNavAction } from './context/search/search-focus-context';

// What a mounted page asks of the nav bar: a tint over its gallery band, and blur depth behind pinned sections.
export { useSetExtensionTint, useExtendNavbarBlur, type ExtensionTint } from './context/navbar-chrome-context';

// `useShortcut` only takes effect below a `KeyboardShortcutsProvider`, which `AppProviders` mounts.
export { useShortcut, type UseShortcutOptions } from './hooks/use-shortcut';
export {
    KeyboardShortcutsProvider,
    useKeyboardShortcuts,
    type ShortcutInfo
} from './context/keyboard-shortcuts-context';

export { useSignal, type Signal } from './hooks/use-signal';
export { useSignalEffect } from './hooks/use-signal-effect';
export { useDebouncedCallback, type DebouncedCallback } from './hooks/use-debounced-callback';
export {
    useGridCursor,
    type GridCursor,
    type GridCursorOptions,
    type GridContainerProps,
    type GridItemProps,
    type GridStep
} from './hooks/use-grid-cursor';
// `useInView` watches through `ViewportObserverContext`; tests provide a controllable observer there.
export { useInView, type UseInViewOptions } from './hooks/use-in-view';
export { ViewportObserverContext, type ViewportObserver } from './context/viewport-observer-context';
