/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

// Test-only module: fast refresh doesn't apply, so components and helpers coexist here.
/* eslint-disable react-refresh/only-export-components */

import { ComponentType, FunctionComponent, ReactElement, ReactNode } from 'react';
import { QueryClient } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { InitialEntry, MemoryRouter } from 'react-router';
import { render, renderHook, RenderHookOptions, RenderOptions } from '@testing-library/react';
import { AppProviders } from '../../../src/app-providers';
import { MainContext } from '../../../src/context';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';
import { PageSettings } from '../../../src/page-settings';
import createDefaultTheme from '../../../src/default/theme';

/**
 * The app's provider stack for tests. It reuses the real `AppProviders` (query
 * client, MainContext, keyboard shortcuts, search) so tests track the app
 * automatically, and adds the entry-shell bits `AppProviders` doesn't own — the
 * MUI theme and a router. Use it whenever a component or hook under test reads
 * any of those. It's a real `MemoryRouter`, so drive history navigation with the
 * `useNavigate()` from the hook under test — `navigate(-1)` / `navigate(1)` are POPs.
 */

const testTheme = createDefaultTheme('light');

/**
 * Fresh per render: `retry: false` (no backoff waits) and `gcTime: Infinity` so a
 * query isn't garbage-collected mid-assertion (TanStack's test recommendation).
 * A new client per render is what keeps tests isolated.
 */
export function createTestQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: { retry: false, gcTime: Infinity, staleTime: 0 },
            mutations: { retry: false }
        }
    });
}

export interface ProviderOptions {
    /** Initial entry for the MemoryRouter (default '/'); an object seeds router state too. */
    route?: InitialEntry;
    /** Reuse a specific client (e.g. to assert cache state); defaults to a fresh one. */
    queryClient?: QueryClient;
    /** Override MainContext fields — most often `{ service }` with the methods under test stubbed. */
    mainContext?: Partial<MainContext>;
}

function mainContextValue(overrides?: Partial<MainContext>): MainContext {
    return {
        service: {} as ExtensionRegistryService,
        // `elements` is a required PageSettings field; the empty object keeps this default genuinely
        // well-typed rather than relying on `as PageSettings` to paper over a missing required field.
        pageSettings: { elements: {} } as PageSettings,
        handleError: () => {},
        userLoading: false,
        updateUser: () => {},
        ...overrides
    };
}

/**
 * Only the entry-shell providers the library expects from whoever mounts it (theme,
 * router). Use it for components that mount `AppProviders` themselves — `Main` above
 * all; everything else wants `TestProviders`.
 */
export function TestEntryShell({ children, route = '/' }: { children: ReactNode; route?: string }) {
    return (
        <ThemeProvider theme={testTheme}>
            <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </ThemeProvider>
    );
}

export function TestProviders({
    children,
    route = '/',
    queryClient,
    mainContext
}: ProviderOptions & { children: ReactNode }) {
    return (
        <TestEntryShell route={route}>
            <AppProviders
                mainContext={mainContextValue(mainContext)}
                queryClient={queryClient ?? createTestQueryClient()}>
                {children}
            </AppProviders>
        </TestEntryShell>
    );
}

/** `render` with only the entry shell — the counterpart of {@link TestEntryShell}. */
export function renderInEntryShell(
    ui: ReactElement,
    options: { route?: string } & Omit<RenderOptions, 'wrapper'> = {}
) {
    const { route, ...rtl } = options;
    return render(ui, {
        wrapper: ({ children }) => <TestEntryShell route={route}>{children}</TestEntryShell>,
        ...rtl
    });
}

/** `render` with the app providers around it. Extra RTL options pass through. */
export function renderWithProviders(ui: ReactElement, options: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {}) {
    const { route, queryClient, mainContext, ...rtl } = options;
    return render(ui, {
        wrapper: ({ children }) => (
            <TestProviders route={route} queryClient={queryClient} mainContext={mainContext}>
                {children}
            </TestProviders>
        ),
        ...rtl
    });
}

/** `renderHook` with the app providers around it — the idiomatic way to test a hook's return. */
export function renderHookWithProviders<Result, Props>(
    hook: (initialProps: Props) => Result,
    options: ProviderOptions & Omit<RenderHookOptions<Props>, 'wrapper'> = {}
) {
    const { route, queryClient, mainContext, ...rtl } = options;
    return renderHook(hook, {
        wrapper: ({ children }) => (
            <TestProviders route={route} queryClient={queryClient} mainContext={mainContext}>
                {children}
            </TestProviders>
        ),
        ...rtl
    });
}

/**
 * HOC form: wraps a component in the app providers so it can be rendered (or
 * exported) already provider-ready — handy for `.map` render lists, stories, or
 * passing a ready-made element where a wrapper option isn't available.
 */
export function withProviders<P extends object>(
    Component: ComponentType<P>,
    options: ProviderOptions = {}
): FunctionComponent<P> {
    const Wrapped: FunctionComponent<P> = props => (
        <TestProviders {...options}>
            <Component {...props} />
        </TestProviders>
    );
    Wrapped.displayName = `withProviders(${Component.displayName ?? Component.name ?? 'Component'})`;
    return Wrapped;
}
