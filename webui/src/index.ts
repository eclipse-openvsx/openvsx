/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

export * from './main';
export * from './page-settings';
export * from './extension-registry-service';
export * from './extension-registry-types';
export * from './pages/extension-detail/extension-detail';
export * from './components/extension-list';
export * from './pages/namespace-detail/namespace-detail';
export * from './pages/user/user-settings';

export * from './components/kbd-key';
export * from './components/openvsx-mark';

// Building blocks of the built-in home page, for composing custom home pages.
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
export { ExtensionCard, type ExtensionCardProps } from './components/extension-card';
// The nav's Publish button: the link to the publish page, the keyboard shortcut behind it, and the
// app's drop target for .vsix packages, so a custom menu keeps drag-and-drop publishing.
export { PublishButton, type PublishButtonProps } from './components/publish/publish-button';
export { CategoryPill, type CategoryPillProps } from './components/category-pill';
export { Pill } from './components/pill';
export * from './components/page-primitives';
export * from './components/page-container';
// Leaf hook modules keep their helpers private, so `export *` exposes only the
// public hook plus its types (e.g. useSearch + SearchFilter).
export * from './hooks/use-search';
export { useRegisterPageSearchBar } from './context/search/page-search-bar-context';

// Keyboard shortcuts: register shortcuts from custom pages/components. The hook
// only takes effect below a KeyboardShortcutsProvider — the built-in AppLayout
// mounts one, so custom layouts need to mount their own.
export { useShortcut, type UseShortcutOptions } from './hooks/use-shortcut';
export {
    KeyboardShortcutsProvider,
    useKeyboardShortcuts,
    type ShortcutInfo
} from './context/keyboard-shortcuts-context';

// Signal: lightweight cross-component coordination primitive. Create one with
// useSignal, subscribe with useSignalEffect.
export * from './hooks/use-signal';
export * from './hooks/use-signal-effect';

// Request layer, for consumers whose registry exposes endpoints this library
// doesn't know about: they build their own service on top of these.
export { sendRequest, sendNonRetriableRequest, type ErrorResponse } from './server-request';
export { controllerFromSignal } from './query-client';

// The app-wide context and the provider stack that supplies it — needed both by
// custom pages reading `service`/`user` and by consumer-side test harnesses.
export { MainContext } from './context';
export { AppProviders } from './app-providers';

// `createAbsoluteURL` and `addQuery` are the other half of the request layer above:
// building an endpoint against `service.serverUrl` needs them.
export { createRoute, createAbsoluteURL, addQuery, formatCompactNumber, formatRating, toRelativeTime } from './utils';
export { NotFound } from './not-found';

// Theme tokens shared with the library chrome, so custom pages line up with it.
export { default as createDefaultTheme, MONO_FONT, NAVBAR_HEIGHT, NAVBAR_HEIGHT_PX } from './default/theme';

export * from './hooks/use-debounced-callback';
export * from './hooks/use-grid-cursor';

// Navbar chrome: what a mounted page asks of the nav bar — a tint over its
// gallery band, and extra blur depth to back sections pinned under the bar.
export { useSetExtensionTint, useExtendNavbarBlur, type ExtensionTint } from './context/navbar-chrome-context';
export { useSearchFocus, type ResultsNavAction } from './context/search/search-focus-context';
export { usePageSearchBar, type PageSearchBarValue } from './context/search/page-search-bar-context';

export { useCategories, CATEGORY_ICONS, DefaultCategoryIcon } from './components/categories';

// Route paths, for linking into the built-in pages.
export { ExtensionDetailRoutes } from './pages/extension-detail/extension-detail-routes';

// Shape of the pages contributed through `PageSettings.elements.adminPages`.
export type { AdminPage, AdminPageCategory } from './pages/admin-dashboard/nav-types';
