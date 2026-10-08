/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { ExtensionDetailOverview } from '../../../../src/pages/extension-detail/extension-detail-overview';
import { Extension } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const extension = (overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        version: '1.0.0',
        versionAlias: [],
        files: {},
        allVersions: { '1.0.0': [] },
        downloads: {},
        downloadable: false,
        deprecated: false,
        namespaceDisplayName: 'Foo',
        galleryColor: '',
        galleryTheme: '',
        ...overrides
    }) as Extension;

describe('ExtensionDetailOverview', () => {
    it('links to the extension in a compatible client', async () => {
        renderWithProviders(<ExtensionDetailOverview extension={extension()} selectVersion={() => {}} />);

        expect(await screen.findByRole('link', { name: 'Open in Client' })).toHaveAttribute(
            'href',
            'vscode:extension/foo.bar'
        );
    });
});
