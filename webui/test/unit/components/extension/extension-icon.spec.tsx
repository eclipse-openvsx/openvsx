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

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
import { createTestViewport } from '../../support/viewport';
import { ExtensionIcon } from '../../../../src/components/extension/extension-icon';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { PageSettings } from '../../../../src/page-settings';
import { SearchEntry } from '../../../../src/extension-registry-types';

const entry = (files: Record<string, string>): SearchEntry =>
    ({
        namespace: 'foo',
        name: 'bar',
        displayName: 'Bar',
        version: '1.0.0',
        targetPlatform: 'universal',
        files
    }) as unknown as SearchEntry;

function renderIcon(files: Record<string, string>) {
    const viewport = createTestViewport();
    // as the real service does: no icon file, no request to make
    const getExtensionIcon = vi.fn(async (_abortController, extension) =>
        extension.files?.icon ? 'blob:icon' : undefined
    );
    renderWithProviders(<ExtensionIcon extension={entry(files)} />, {
        mainContext: {
            service: { getExtensionIcon } as unknown as ExtensionRegistryService,
            pageSettings: { urls: { extensionDefaultIcon: '/default-icon.png' } } as PageSettings
        },
        viewport: viewport.observer
    });
    return { getExtensionIcon, viewport };
}

describe('ExtensionIcon', () => {
    it('requests nothing until the icon comes near the viewport', async () => {
        const { getExtensionIcon, viewport } = renderIcon({ icon: 'https://example.test/icon.png' });

        expect(getExtensionIcon).not.toHaveBeenCalled();
        expect(screen.queryByRole('img')).not.toBeInTheDocument();

        viewport.setInView(true);

        await waitFor(() => expect(getExtensionIcon).toHaveBeenCalledOnce());
        expect(await screen.findByRole('img')).toHaveAttribute('src', 'blob:icon');
    });

    // Synchronously, not findBy: there is nothing to fetch, so not even a frame of skeleton.
    it('shows the default icon without waiting when the extension has none', () => {
        const { viewport } = renderIcon({});

        expect(screen.getByRole('img')).toHaveAttribute('src', '/default-icon.png');
        expect(viewport.observed()).toHaveLength(0);
    });
});
