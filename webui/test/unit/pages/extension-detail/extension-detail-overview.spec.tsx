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
import { screen } from '@testing-library/react';
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

describe('ExtensionDetailOverview', () => {
    it('shows the download size of the extension package', async () => {
        renderOverview({ downloadSize: 5 * 1024 * 1024 });

        expect(await screen.findByText('Size')).toBeInTheDocument();
        expect(screen.getByText('5.00 MB')).toBeInTheDocument();
    });

    // An extension published before the registry recorded sizes has none until the backfill reaches it.
    // The section is left out entirely rather than shown empty or as a misleading zero.
    it('omits the size when the registry does not know it', async () => {
        renderOverview();

        expect(await screen.findByText('Unique Identifier')).toBeInTheDocument();
        expect(screen.queryByText('Size')).not.toBeInTheDocument();
    });
});
