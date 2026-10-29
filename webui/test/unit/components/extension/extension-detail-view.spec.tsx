/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
import { testUser } from '../../support/trusted-publishing';
import { ExtensionDetailView } from '../../../../src/components/extension/extension-detail-view';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, VersionTargetPlatforms } from '../../../../src/extension-registry-types';
import { PageSettings } from '../../../../src/page-settings';

const version = (overrides: Partial<VersionTargetPlatforms> = {}): VersionTargetPlatforms => ({
    version: '1.0.0',
    targetPlatforms: [{ targetPlatform: 'universal', active: true, removed: false }],
    ...overrides
});

const extension = (versions: VersionTargetPlatforms[], overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        version: '1.0.0',
        publishedLatestVersion: '1.0.0',
        displayName: 'Bar Tools',
        files: {},
        downloadCount: 0,
        reviewCount: 0,
        deprecated: false,
        active: true,
        allTargetPlatformVersions: versions,
        ...overrides
    }) as unknown as Extension;

function renderDetail(
    versions: VersionTargetPlatforms[],
    onPurgeVersion?: () => Promise<unknown>,
    overrides: { extension?: Partial<Extension>; pageSettings?: Partial<PageSettings> } = {}
) {
    const service = {
        getExtensionIcon: vi.fn().mockResolvedValue(null),
        getTrustedPublishingStatus: vi.fn().mockResolvedValue({ enabled: false, allowed: false })
    } as unknown as ExtensionRegistryService;
    return renderWithProviders(
        <ExtensionDetailView
            extension={extension(versions, overrides.extension)}
            onRemoveVersion={vi.fn().mockResolvedValue(undefined)}
            onVersionDeleted={vi.fn()}
            onPurgeVersion={onPurgeVersion}
        />,
        {
            mainContext: {
                service,
                user: testUser,
                pageSettings: {
                    urls: { extensionDefaultIcon: '/icon.png' },
                    elements: {},
                    ...overrides.pageSettings
                } as PageSettings
            }
        }
    );
}

// Each danger-zone row repeats its title above the button, so query the button itself.
const dangerButton = (name: string) => screen.getByRole('button', { name });
const noDangerButton = (name: string) => screen.queryByRole('button', { name });

// Stands in for a deployment's configured `pageSettings.elements.claimNamespace`: this component's
// own logic is only responsible for passing the namespace (and extension, where it has one)
// implementation renders.
const ClaimNamespaceStub: FunctionComponent<{ namespace: string }> = ({ namespace }) => <span>Claim {namespace}</span>;

describe('ExtensionDetailView', () => {
    it('keeps the purge affordances out of the way without a purge handler', () => {
        renderDetail([version()]);

        expect(dangerButton('Delete all versions')).toBeInTheDocument();
        expect(noDangerButton('Purge all versions')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Purge version 1.0.0')).not.toBeInTheDocument();
    });

    it('offers purging once a purge handler is supplied', () => {
        renderDetail([version()], vi.fn().mockResolvedValue(undefined));

        expect(dangerButton('Purge all versions')).toBeInTheDocument();
        expect(screen.getByLabelText('Purge version 1.0.0')).toBeInTheDocument();
    });

    it('disables "Delete all versions" when nothing is left to delete', () => {
        const removed = version({ targetPlatforms: [{ targetPlatform: 'universal', active: false, removed: true }] });
        renderDetail([removed]);

        expect(dangerButton('Delete all versions')).toBeDisabled();
    });

    it('disables "Delete all versions" when the user published none of them', () => {
        renderDetail([version({ canDelete: false })]);

        expect(dangerButton('Delete all versions')).toBeDisabled();
    });

    it('enables "Delete all versions" when at least one is the user\'s to delete', () => {
        renderDetail([version({ canDelete: false }), version({ version: '1.1.0', canDelete: true })]);

        expect(dangerButton('Delete all versions')).toBeEnabled();
    });

    it('hides the danger zone entirely for an extension with no versions', () => {
        renderDetail([]);

        expect(screen.queryByText('Danger Zone')).not.toBeInTheDocument();
    });

    it('does not call any version "Latest" while the registry is holding the extension back', () => {
        renderDetail([version()], undefined, { extension: { active: false, publishedLatestVersion: undefined } });

        expect(screen.queryByText('Latest')).not.toBeInTheDocument();
    });

    it('marks the latest version of a live extension', () => {
        renderDetail([version()], undefined, { extension: { active: true } });

        expect(screen.getByText('Latest')).toBeInTheDocument();
    });

    it('uses the published latest instead of a higher removed version', () => {
        renderDetail(
            [
                version({
                    version: '2.0.0',
                    targetPlatforms: [{ targetPlatform: 'universal', active: false, removed: true }]
                }),
                version({ version: '1.0.0' })
            ],
            undefined,
            { extension: { version: '2.0.0', publishedLatestVersion: '1.0.0' } }
        );

        expect(screen.getByText((_, element) => element?.textContent === '1.0.0Latest')).toBeInTheDocument();
        expect(screen.getByLabelText('Delete version 2.0.0')).toBeDisabled();
    });

    it('does not show historical metadata as latest when no version is public', () => {
        renderDetail([version()], undefined, { extension: { version: '1.0.0', publishedLatestVersion: undefined } });

        expect(screen.queryByText('Latest')).not.toBeInTheDocument();
        expect(screen.getAllByText('—').length).toBeGreaterThan(0);
    });

    it('shows the upstream latest without marking an inactive local row', () => {
        renderDetail(
            [
                version({
                    version: '2.0.0',
                    targetPlatforms: [{ targetPlatform: 'universal', active: false, removed: false }]
                })
            ],
            undefined,
            { extension: { version: '2.0.0', publishedLatestVersion: '2.0.0' } }
        );
        expect(screen.getByText(/upstream/)).toBeInTheDocument();
        expect(screen.queryByText('Latest')).not.toBeInTheDocument();
    });

    it('updates the latest marker when refreshed data activates a local version', () => {
        const inactive = version({
            version: '2.0.0',
            targetPlatforms: [{ targetPlatform: 'universal', active: false, removed: false }]
        });
        const view = renderDetail([inactive], undefined, { extension: { publishedLatestVersion: '3.0.0' } });
        expect(screen.getByText(/upstream/)).toBeInTheDocument();
        expect(screen.queryByText('Latest')).not.toBeInTheDocument();

        view.rerender(
            <ExtensionDetailView
                extension={extension([version({ version: '2.0.0' })], { publishedLatestVersion: '2.0.0' })}
                onRemoveVersion={vi.fn().mockResolvedValue(undefined)}
                onVersionDeleted={vi.fn()}
            />
        );
        expect(screen.queryByText(/upstream/)).not.toBeInTheDocument();
        expect(screen.getByText('Latest')).toBeInTheDocument();
    });

    describe('namespace ownership conflict', () => {
        it('renders the configured claimNamespace element when there is a conflict', () => {
            renderDetail([], undefined, {
                extension: { active: false, namespaceOwnershipConflict: true },
                pageSettings: { elements: { claimNamespace: ClaimNamespaceStub } }
            });

            expect(screen.getByText('Claim foo')).toBeInTheDocument();
        });

        it('does not render claimNamespace without a namespace ownership conflict', () => {
            renderDetail([], undefined, {
                extension: { active: true },
                pageSettings: { elements: { claimNamespace: ClaimNamespaceStub } }
            });

            expect(screen.queryByText('Claim foo')).not.toBeInTheDocument();
        });

        it('falls back to a generic namespace-access link when no claimNamespace element is configured', () => {
            renderDetail([], undefined, {
                extension: { active: false, namespaceOwnershipConflict: true },
                pageSettings: {
                    elements: {},
                    urls: { namespaceAccessInfo: 'https://example.test/namespace-access' } as PageSettings['urls']
                }
            });

            expect(screen.queryByText('Claim foo')).not.toBeInTheDocument();
            const link = screen.getByRole('link', { name: 'Claim ownership' });
            expect(link).toHaveAttribute('href', 'https://example.test/namespace-access');
        });

        it('hides the claim action in the admin view, keeping the explanation', () => {
            // A purge handler is only ever supplied by the admin dashboard (see ExtensionDetailViewProps).
            renderDetail([], vi.fn().mockResolvedValue(undefined), {
                extension: { active: false, namespaceOwnershipConflict: true },
                pageSettings: { elements: { claimNamespace: ClaimNamespaceStub } }
            });

            expect(screen.queryByText('Claim foo')).not.toBeInTheDocument();
            expect(screen.queryByRole('link', { name: 'Claim ownership' })).not.toBeInTheDocument();
            expect(
                screen.getByText(/stays inactive until the namespace is claimed and ownership verified/)
            ).toBeInTheDocument();
        });

        it('explains that the namespace needs to be claimed before the extension can be activated', () => {
            renderDetail([], undefined, {
                extension: { active: false, namespaceOwnershipConflict: true },
                pageSettings: { elements: { claimNamespace: ClaimNamespaceStub } }
            });

            expect(
                screen.getByText(/stays inactive until the namespace is claimed and ownership verified/)
            ).toBeInTheDocument();
        });

        it('does not show the namespace ownership explanation without a conflict', () => {
            renderDetail([], undefined, {
                extension: { active: true },
                pageSettings: { elements: { claimNamespace: ClaimNamespaceStub } }
            });

            expect(screen.queryByText(/needs to be claimed/)).not.toBeInTheDocument();
        });
    });
});
