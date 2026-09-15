/******************************************************************************
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
 *****************************************************************************/

import { FunctionComponent, ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient as defaultQueryClient } from './query-client';
import { MainContext } from './context';
import { KeyboardShortcutsProvider } from './context/keyboard-shortcuts-context';
import { SearchProvider } from './context/search/search-context';
import { PublishQueueProvider } from './context/publish-queue-context';

interface AppProvidersProps {
    /** Built by `Main` from its props and state; supplied here so this stays the one provider site. */
    mainContext: MainContext;
    /** Defaults to the shared singleton; injected only in tests (a fresh, no-retry client). */
    queryClient?: QueryClient;
    children: ReactNode;
}

/**
 * The single home for the app-wide providers, so the whole stack reads top to
 * bottom in one place. Ordered outer→inner; keyboard shortcuts and search wrap
 * every route (admin included). Router, theme, and Helmet stay at the app entry —
 * they're supplied by whoever mounts the library. Lower-tier, feature-scoped
 * providers (e.g. the navbar chrome) stay with their feature.
 */
export const AppProviders: FunctionComponent<AppProvidersProps> = ({
    mainContext,
    queryClient = defaultQueryClient,
    children
}) => (
    <QueryClientProvider client={queryClient}>
        <MainContext.Provider value={mainContext}>
            <KeyboardShortcutsProvider>
                <SearchProvider>
                    <PublishQueueProvider>{children}</PublishQueueProvider>
                </SearchProvider>
            </KeyboardShortcutsProvider>
        </MainContext.Provider>
        <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
);
