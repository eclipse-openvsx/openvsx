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

import { FunctionComponent, useContext } from 'react';
import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useQuery } from '@tanstack/react-query';
import { renderInEntryShell } from './support/test-providers';
import { Main } from '../../src/main';
import { queryClient } from '../../src/query-client';
import { MainContext } from '../../src/context';
import { useReportedQuery } from '../../src/hooks/use-reported-query';
import { PageSettings } from '../../src/page-settings';
import { UserData } from '../../src/extension-registry-types';
import { ExtensionRegistryService } from '../../src/extension-registry-service';

// Every context value the probe rendered with, so tests can compare identities across renders.
let seenContexts: MainContext[] = [];

// Stands in for a consumer page: the only way to observe what the context carries.
const ContextProbe: FunctionComponent = () => {
    const context = useContext(MainContext);
    seenContexts.push(context);
    const { userLoading, user } = context;
    return <div data-testid='probe'>{`loading=${userLoading} user=${user?.loginName ?? 'none'}`}</div>;
};

// A 4xx status so the query client reports the failure without retrying it.
const failure = Object.assign(new Error('boom'), { status: 400 });

// How the registry answers a visitor who is not logged in.
const notLoggedIn = { error: 'Not logged in.', status: 200 };

// A consumer page whose query keeps failing, reported to the dialog through MainContext.
const failingQueryFn = vi.fn(() => Promise.reject(failure));
const FailingQueryReporter: FunctionComponent = () => {
    useReportedQuery(useQuery({ queryKey: ['main-spec-failing'], queryFn: failingQueryFn }));
    return null;
};
let failingPageRenders = 0;
// A report loop re-renders synchronously inside act() and would hang the test; capping the
// renders turns it into a failed assertion instead.
const FailingQueryPage: FunctionComponent = () => {
    failingPageRenders++;
    return failingPageRenders < 50 ? <FailingQueryReporter /> : null;
};

function renderMain(home: FunctionComponent = ContextProbe, overrides: Partial<ExtensionRegistryService> = {}) {
    const pageSettings = {
        pageTitle: 'test',
        // A custom home component replaces the built-in landing page, so it is what '/' renders.
        elements: { home },
        urls: { extensionDefaultIcon: '', namespaceAccessInfo: '' }
    } as PageSettings;
    let settleUser = { resolve: (_user: UserData) => {}, reject: (_err: unknown) => {} };
    const service = {
        getUser: vi.fn(() => new Promise<UserData>((resolve, reject) => (settleUser = { resolve, reject }))),
        getRegistryVersion: vi.fn().mockResolvedValue({ version: '1.0.0' }),
        ...overrides
    } as unknown as ExtensionRegistryService;
    // `loginProviders` supplied so Main skips its own login-providers fetch.
    const loginProviders = {};
    const main = () => <Main service={service} pageSettings={pageSettings} loginProviders={loginProviders} />;
    const { rerender } = renderInEntryShell(main());
    return {
        service,
        resolveUser: (user: UserData) => settleUser.resolve(user),
        rejectUser: (err: unknown) => settleUser.reject(err),
        rerender: () => rerender(main())
    };
}

describe('Main', () => {
    beforeEach(() => {
        seenContexts = [];
        failingPageRenders = 0;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        window.history.replaceState(null, '', '/');
    });

    it('exposes userLoading on MainContext while the initial user fetch is in flight', async () => {
        const { resolveUser } = renderMain();

        expect(screen.getByTestId('probe')).toHaveTextContent('loading=true user=none');

        resolveUser({ loginName: 'testuser', tokensUrl: '', createTokenUrl: '' });

        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=testuser'));
    });

    it('treats a visitor who is not logged in as logged out, without reporting it', async () => {
        const { rejectUser } = renderMain();

        rejectUser(notLoggedIn);

        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=none'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('reports a failure to load the user', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        const { rejectUser } = renderMain();

        rejectUser({ error: 'Forbidden', message: 'no access', status: 403 });

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('no access');
        expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=none');
    });

    it('keeps handleError and updateUser stable while the context state changes', async () => {
        const { resolveUser } = renderMain();
        const initial = seenContexts[0];

        resolveUser({ loginName: 'testuser', tokensUrl: '', createTokenUrl: '' });
        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=testuser'));

        const latest = seenContexts[seenContexts.length - 1];
        expect(latest).not.toBe(initial);
        expect(latest.handleError).toBe(initial.handleError);
        expect(latest.updateUser).toBe(initial.updateUser);
    });

    it('keeps the context value when Main re-renders with unchanged props and state', async () => {
        const { resolveUser, rerender } = renderMain();
        resolveUser({ loginName: 'testuser', tokensUrl: '', createTokenUrl: '' });
        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=testuser'));
        const before = seenContexts[seenContexts.length - 1];
        const rendersBefore = seenContexts.length;

        rerender();

        expect(seenContexts.length).toBeGreaterThan(rendersBefore);
        expect(seenContexts[seenContexts.length - 1]).toBe(before);
    });

    it('reports a failing query once and keeps the error dialog closed after it is dismissed', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        const user = userEvent.setup();
        const { rejectUser } = renderMain(FailingQueryPage);
        rejectUser(notLoggedIn);

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('An unexpected error occurred: boom');

        await user.click(screen.getByRole('button', { name: 'Close' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

        // Let any effect re-run that would report the same error again.
        await new Promise(resolve => setTimeout(resolve, 50));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(failingPageRenders).toBeLessThan(10);
        expect(failingQueryFn).toHaveBeenCalledTimes(1);
        expect(console.error).toHaveBeenCalledTimes(1);
    });

    it('shows the error the last login failed with when redirected back with auth-error', async () => {
        window.history.replaceState(null, '', '/?auth-error');
        const getUserAuthError = vi
            .fn()
            .mockRejectedValue({ error: 'Login failed', message: 'Login failed', status: 200 });
        const { rejectUser } = renderMain(ContextProbe, { getUserAuthError });
        rejectUser(notLoggedIn);

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Login failed');
        expect(getUserAuthError).toHaveBeenCalledTimes(1);
    });

    // A stale `?auth-error`, say a bookmarked one, finds no login error in the session: a 404, not a failure.
    it('says nothing when the session holds no login error', async () => {
        window.history.replaceState(null, '', '/?auth-error');
        const getUserAuthError = vi.fn().mockRejectedValue({ error: 'Not Found', status: 404 });
        const { rejectUser } = renderMain(ContextProbe, { getUserAuthError });
        rejectUser(notLoggedIn);

        await waitFor(() => expect(queryClient.getQueryState(['auth-error'])?.status).toBe('error'));
        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=none'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('refetches the user when a page asks for it', async () => {
        let updateUser: () => void = () => {};
        const UpdatingProbe: FunctionComponent = () => {
            updateUser = useContext(MainContext).updateUser;
            return <ContextProbe />;
        };
        const { resolveUser, rejectUser, service } = renderMain(UpdatingProbe);
        rejectUser(notLoggedIn);
        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=none'));

        updateUser();
        await waitFor(() => expect(service.getUser).toHaveBeenCalledTimes(2));
        resolveUser({ loginName: 'testuser', tokensUrl: '', createTokenUrl: '' });

        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=testuser'));
    });
});
