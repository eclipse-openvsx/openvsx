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
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderInEntryShell } from './support/test-providers';
import { Main } from '../../src/main';
import { MainContext } from '../../src/context';
import { PageSettings } from '../../src/page-settings';
import { ErrorResult, UserData } from '../../src/extension-registry-types';
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

const pageSettings = {
    pageTitle: 'test',
    // A custom home component replaces the built-in landing page, so the probe is what '/' renders.
    elements: { home: ContextProbe },
    urls: { extensionDefaultIcon: '', namespaceAccessInfo: '' }
} as PageSettings;

function renderMain() {
    let resolveUser: (result: UserData | ErrorResult) => void = () => {};
    const service = {
        getUser: vi.fn(() => new Promise<UserData | ErrorResult>(resolve => (resolveUser = resolve))),
        getRegistryVersion: vi.fn().mockResolvedValue({ version: '1.0.0' })
    } as unknown as ExtensionRegistryService;
    // `loginProviders` supplied so Main skips its own login-providers fetch.
    const loginProviders = {};
    const main = () => <Main service={service} pageSettings={pageSettings} loginProviders={loginProviders} />;
    const { rerender } = renderInEntryShell(main());
    return {
        resolveUser: (result: UserData | ErrorResult) => resolveUser(result),
        rerender: () => rerender(main())
    };
}

describe('Main', () => {
    beforeEach(() => {
        seenContexts = [];
    });

    it('exposes userLoading on MainContext while the initial user fetch is in flight', async () => {
        const { resolveUser } = renderMain();

        expect(screen.getByTestId('probe')).toHaveTextContent('loading=true user=none');

        resolveUser({ loginName: 'testuser', tokensUrl: '', createTokenUrl: '' });

        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=testuser'));
    });

    it('clears userLoading when the user turns out not to be logged in', async () => {
        const { resolveUser } = renderMain();

        // An error result with HTTP OK is how the server reports "not logged in".
        resolveUser({ error: 'Not logged in' });

        await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('loading=false user=none'));
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
});
