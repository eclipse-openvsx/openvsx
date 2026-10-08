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
import { FunctionComponent } from 'react';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { testUser } from '../../support/trusted-publishing';
import { PublishQueueStrip } from '../../../../src/components/publish/publish-queue-strip';
import { PublishQueue, usePublishQueue } from '../../../../src/context/publish-queue-context';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension } from '../../../../src/extension-registry-types';
import { PageSettings } from '../../../../src/page-settings';

const vsix = (name: string) => new File(['package'], name, { type: 'application/vsix' });

const published = (overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        displayName: 'Bar Tools',
        version: '1.0.0',
        files: {},
        downloadCount: 0,
        reviewCount: 0,
        deprecated: false,
        ...overrides
    }) as Extension;

/**
 * Renders the strip with a handle on the queue from inside the same provider tree —
 * a second render would get its own provider, and the strip would never see the packages.
 */
function renderStrip(service: Partial<ExtensionRegistryService>) {
    const queue: { current?: PublishQueue } = {};
    const CaptureQueue: FunctionComponent = () => {
        queue.current = usePublishQueue();
        return null;
    };
    let mountKey = 0;
    // Keyed so a test can remount just the strip, the way navigating away and back does,
    // while the queue provider above it survives.
    const tree = (key: number) => (
        <>
            <CaptureQueue />
            <PublishQueueStrip key={key} />
        </>
    );
    const { rerender } = renderWithProviders(tree(mountKey), {
        mainContext: {
            service: {
                getExtensionIcon: vi.fn().mockResolvedValue(null),
                ...service
            } as unknown as ExtensionRegistryService,
            user: testUser,
            pageSettings: { urls: { extensionDefaultIcon: '/icon.png' } } as PageSettings
        }
    });
    return {
        publish: async (files: File[]) => {
            await act(async () => {
                await queue.current?.publish(files);
            });
        },
        remountStrip: () => {
            mountKey += 1;
            rerender(tree(mountKey));
        }
    };
}

describe('PublishQueueStrip', () => {
    it('shows nothing until something is being published', async () => {
        renderStrip({ publishExtension: vi.fn() });

        expect(screen.queryByLabelText('Publishing queue')).not.toBeInTheDocument();
    });

    it('stands in with the card skeleton while a package uploads', async () => {
        const { publish } = renderStrip({ publishExtension: vi.fn().mockReturnValue(new Promise(() => {})) });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByLabelText('Publishing queue')).toBeInTheDocument();
        expect(screen.getByText('Publishing 1')).toBeInTheDocument();
        expect(screen.queryByText('Bar Tools')).not.toBeInTheDocument();
    });

    it('swaps in the real extension card once the registry accepts the package', async () => {
        const { publish } = renderStrip({ publishExtension: vi.fn().mockResolvedValue(published()) });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByText('Bar Tools')).toBeInTheDocument();
    });

    it('sends a published package to its settings page, gear and all', async () => {
        const { publish } = renderStrip({ publishExtension: vi.fn().mockResolvedValue(published()) });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByLabelText('Bar Tools')).toHaveAttribute('href', '/user-settings/extensions/foo/bar');
    });

    it('marks a package still under review', async () => {
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockResolvedValue(published({ reviewStatus: 'under_review' })),
            getExtension: vi
                .fn()
                .mockResolvedValueOnce(published({ reviewStatus: 'under_review' }))
                .mockReturnValue(new Promise(() => {}))
        });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByText('Under review')).toBeInTheDocument();
        // The queue as a whole says what it is waiting on, rather than still claiming to upload.
        expect(screen.getByText('Reviewing 1')).toBeInTheDocument();
        expect(screen.queryByText('Publishing 1')).not.toBeInTheDocument();
    });

    it('names the unverified namespace holding a package back, rather than calling it a review', async () => {
        const conflicted = published({ reviewStatus: 'under_review', namespaceOwnershipConflict: true });
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockResolvedValue(conflicted),
            getExtensions: vi.fn().mockResolvedValue([conflicted])
        });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByText('Namespace not verified')).toBeInTheDocument();
        expect(screen.queryByText('Under review')).not.toBeInTheDocument();
        // And the line as a whole says something is waiting on the user, not that it all landed.
        expect(screen.getByText('Action needed')).toBeInTheDocument();
    });

    it('keeps the icon skeleton up while the package is still being processed', async () => {
        const withoutIcon = published({ files: {} as Extension['files'] });
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockResolvedValue(withoutIcon),
            getExtensions: vi.fn().mockResolvedValue([withoutIcon]),
            getExtensionIcon: vi.fn().mockResolvedValue(null)
        });

        await publish([vsix('one.vsix')]);

        // The name lands straight away; the icon slot stays a skeleton rather than
        // falling back to the placeholder, so the image never appears.
        expect(await screen.findByText('Bar Tools')).toBeInTheDocument();
        await expect(screen.findByAltText('Bar Tools')).rejects.toThrow();
    });

    it('shows the icon once the pipeline has stored it', async () => {
        const withIcon = published({ files: { icon: 'https://registry.test/icon.png' } as Extension['files'] });
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockResolvedValue(withIcon),
            getExtensions: vi.fn().mockResolvedValue([withIcon]),
            getExtensionIcon: vi.fn().mockResolvedValue('blob:icon')
        });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByAltText('Bar Tools')).toBeInTheDocument();
    });

    it('shows the reason a 400 gives back, immutable versions included', async () => {
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockRejectedValue({
                deprecated: false,
                downloadable: false,
                status: 400,
                error: 'Extension Catppuccin.catppuccin-vsc 3.19.0 is already published and was removed.'
            })
        });

        await publish([vsix('catppuccin.vsix')]);

        expect(await screen.findByText(/is already published and was removed/)).toBeInTheDocument();
    });

    it('washes the card green once when the registry accepts a package', async () => {
        const { publish } = renderStrip({ publishExtension: vi.fn().mockResolvedValue(published()) });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByTestId('publish-accepted')).toBeInTheDocument();
    });

    it('does not repeat the green wash when the page is revisited', async () => {
        const { publish, remountStrip } = renderStrip({
            publishExtension: vi.fn().mockResolvedValue(published())
        });

        await publish([vsix('one.vsix')]);
        await screen.findByTestId('publish-accepted');

        // Leaving and coming back finds the package already published: no repeat acknowledgement.
        remountStrip();

        expect(await screen.findByText('Bar Tools')).toBeInTheDocument();
        expect(screen.queryByTestId('publish-accepted')).not.toBeInTheDocument();
    });

    it('keeps a failed package in the line with its reason', async () => {
        const { publish } = renderStrip({
            publishExtension: vi.fn().mockRejectedValue({ error: 'Extension too large' })
        });

        await publish([vsix('one.vsix')]);

        expect(await screen.findByText('Extension too large')).toBeInTheDocument();
        expect(screen.getByText('one.vsix')).toBeInTheDocument();
    });

    it('clears the finished packages on request', async () => {
        const { publish } = renderStrip({ publishExtension: vi.fn().mockResolvedValue(published()) });

        await publish([vsix('one.vsix')]);
        await screen.findByText('Bar Tools');

        await userEvent.click(screen.getByText('Clear'));

        await waitFor(() => expect(screen.queryByLabelText('Publishing queue')).not.toBeInTheDocument());
    });
});
