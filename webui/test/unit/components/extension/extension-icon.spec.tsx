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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
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

/** Elements observed by the fake, each with the means to report it has come into view. */
let observed: Array<{ node: Element; rootMargin: string; enterView: () => void }> = [];

/** jsdom implements no IntersectionObserver, and the component's whole point is what it defers. */
class FakeIntersectionObserver implements IntersectionObserver {
    readonly root = null;
    readonly rootMargin: string;
    readonly thresholds = [];

    constructor(
        private readonly callback: IntersectionObserverCallback,
        options?: IntersectionObserverInit
    ) {
        this.rootMargin = options?.rootMargin ?? '';
    }

    observe(node: Element): void {
        observed.push({
            node,
            rootMargin: this.rootMargin,
            enterView: () => this.callback([{ isIntersecting: true, target: node } as IntersectionObserverEntry], this)
        });
    }

    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): IntersectionObserverEntry[] {
        return [];
    }
}

function renderIcon(files: Record<string, string>) {
    // as the real service does: no icon file, no request to make
    const getExtensionIcon = vi.fn(async (_abortController, extension) =>
        extension.files?.icon ? 'blob:icon' : undefined
    );
    renderWithProviders(<ExtensionIcon extension={entry(files)} />, {
        mainContext: {
            service: { getExtensionIcon } as unknown as ExtensionRegistryService,
            pageSettings: { urls: { extensionDefaultIcon: '/default-icon.png' } } as PageSettings
        }
    });
    return { getExtensionIcon };
}

describe('ExtensionIcon', () => {
    beforeEach(() => {
        observed = [];
        vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('requests nothing until the icon comes near the viewport', async () => {
        const { getExtensionIcon } = renderIcon({ icon: 'https://example.test/icon.png' });

        expect(getExtensionIcon).not.toHaveBeenCalled();
        expect(screen.queryByRole('img')).not.toBeInTheDocument();

        act(() => observed.forEach(element => element.enterView()));

        await waitFor(() => expect(getExtensionIcon).toHaveBeenCalledOnce());
        expect(await screen.findByRole('img')).toHaveAttribute('src', 'blob:icon');
    });

    it('starts loading before the icon is actually on screen', () => {
        renderIcon({ icon: 'https://example.test/icon.png' });

        expect(observed).toHaveLength(1);
        expect(observed[0].rootMargin).not.toBe('');
    });

    it('shows the default icon without waiting when the extension has none', async () => {
        renderIcon({});

        expect(await screen.findByRole('img')).toHaveAttribute('src', '/default-icon.png');
        expect(observed).toHaveLength(0);
    });

    it('loads eagerly where the browser has no IntersectionObserver', async () => {
        vi.unstubAllGlobals();

        const { getExtensionIcon } = renderIcon({ icon: 'https://example.test/icon.png' });

        await waitFor(() => expect(getExtensionIcon).toHaveBeenCalledOnce());
        expect(await screen.findByRole('img')).toHaveAttribute('src', 'blob:icon');
    });
});
