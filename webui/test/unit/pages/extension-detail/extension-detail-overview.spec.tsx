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

import { describe, it, expect } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExtensionDetailOverview } from '../../../../src/pages/extension-detail/extension-detail-overview';
import { Extension } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const extension = (overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        namespaceDisplayName: 'Foo',
        version: '1.0.0',
        displayName: 'Bar Tools',
        // Empty, so the component takes its "no README available" path and needs no service.
        files: {},
        allVersions: { '1.0.0': 'https://example.com/api/foo/bar/1.0.0' },
        versionAlias: [],
        // The detail endpoint always sets this, and the "Works With" section reads it unguarded.
        downloads: {},
        downloadCount: 0,
        reviewCount: 0,
        deprecated: false,
        verified: true,
        publishedBy: { loginName: 'test_user', homepage: 'https://example.com/test_user' },
        ...overrides
    }) as unknown as Extension;

const renderOverview = (overrides: Partial<Extension> = {}) =>
    renderWithProviders(<ExtensionDetailOverview extension={extension(overrides)} selectVersion={() => {}} />);

const platformRow = (name: string): HTMLElement =>
    screen.getAllByRole('listitem').find(row => within(row).queryByText(name)) as HTMLElement;

describe('ExtensionDetailOverview', () => {
    it('lists each target platform in its own row with its download size', async () => {
        renderOverview({
            downloads: { 'linux-x64': 'https://example.com/linux', 'win32-x64': 'https://example.com/win' },
            downloadSizes: { 'linux-x64': 5 * 1024 * 1024, 'win32-x64': 6 * 1024 * 1024 }
        });

        expect(await screen.findByText('Linux x64')).toBeInTheDocument();
        expect(screen.getAllByRole('listitem')).toHaveLength(2);
        expect(within(platformRow('Linux x64')).getByText('5.00 MB')).toBeInTheDocument();
        expect(within(platformRow('Windows x64')).getByText('6.00 MB')).toBeInTheDocument();
    });

    // An extension published before the registry recorded sizes has none until the backfill reaches it.
    // The platform is listed by name alone rather than with a misleading zero.
    it('lists a target platform without a size when the registry does not know it', async () => {
        renderOverview({
            downloads: { 'linux-x64': 'https://example.com/linux', 'win32-x64': 'https://example.com/win' },
            downloadSizes: { 'linux-x64': 5 * 1024 * 1024 }
        });

        expect(await screen.findByText('Linux x64')).toBeInTheDocument();
        expect(within(platformRow('Linux x64')).getByText('5.00 MB')).toBeInTheDocument();
        expect(within(platformRow('Windows x64')).queryByText(/MB|KB|GB/)).not.toBeInTheDocument();
    });

    it('lists the target platforms when no sizes are known at all', async () => {
        renderOverview({ downloads: { universal: 'https://example.com/universal' } });

        expect(await screen.findByText('Universal')).toBeInTheDocument();
        expect(screen.queryByText(/MB|KB|GB/)).not.toBeInTheDocument();
    });

    // The registry stores only the size of the package; the installed extension is larger by its
    // compression ratio, so an info icon beside the heading says what the sizes are.
    it('explains in a tooltip that the sizes are download sizes', async () => {
        renderOverview({
            downloads: { universal: 'https://example.com/universal' },
            downloadSizes: { universal: 5 * 1024 * 1024 }
        });

        const info = await screen.findByLabelText(/installed size is larger/i);
        await userEvent.hover(info);

        expect(await screen.findByRole('tooltip')).toHaveTextContent(/installed size is larger/i);
    });

    it('shows no info icon when no size is shown', async () => {
        renderOverview({ downloads: { universal: 'https://example.com/universal' } });

        expect(await screen.findByText('Universal')).toBeInTheDocument();
        expect(screen.queryByLabelText(/installed size is larger/i)).not.toBeInTheDocument();
    });

    it('links to the extension in a compatible client', async () => {
        renderOverview();

        expect(await screen.findByRole('link', { name: 'Open in Client' })).toHaveAttribute(
            'href',
            'vscode:extension/foo.bar'
        );
    });
});
