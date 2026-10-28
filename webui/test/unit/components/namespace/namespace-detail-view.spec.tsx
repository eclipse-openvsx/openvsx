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
import { testUser } from '../../support/trusted-publishing';
import { namespaceDetails, testNamespace } from '../../support/user-settings';
import {
    NamespaceDetailView,
    NamespaceDetailViewProps
} from '../../../../src/components/namespace/namespace-detail-view';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension, Namespace } from '../../../../src/extension-registry-types';
import { PageSettings } from '../../../../src/page-settings';

const extension = (name: string): Extension =>
    ({
        name,
        namespace: 'foo',
        version: '1.0.0',
        displayName: name,
        files: {},
        downloadCount: 0,
        reviewCount: 0,
        deprecated: false
    }) as Extension;

function renderView(namespace: Namespace, props: Partial<NamespaceDetailViewProps> = {}) {
    const getExtensionDetail = vi.fn().mockResolvedValue(extension('bar'));
    const service = {
        getExtensionDetail,
        getExtensionIcon: vi.fn().mockResolvedValue(null),
        getNamespaceDetails: vi.fn().mockResolvedValue(namespaceDetails()),
        getNamespaceMembers: vi.fn().mockResolvedValue({ namespaceMemberships: [] }),
        getTrustedPublishingStatus: vi.fn().mockResolvedValue({ enabled: false, allowed: false })
    } as unknown as ExtensionRegistryService;
    renderWithProviders(
        <NamespaceDetailView
            namespace={namespace}
            setLoadingState={vi.fn()}
            extensionRoutePrefix='/user-settings/extensions'
            {...props}
        />,
        {
            mainContext: {
                service,
                user: testUser,
                pageSettings: {
                    elements: {},
                    urls: { extensionDefaultIcon: '/icon.png' }
                } as unknown as PageSettings
            }
        }
    );
    return { getExtensionDetail, service };
}

describe('NamespaceDetailView', () => {
    it('shows only the sections the namespace grants access to', async () => {
        renderView(testNamespace({ detailsUrl: '', membersUrl: '' }));

        expect(await screen.findByText('Extensions')).toBeInTheDocument();
        expect(screen.queryByText('Details')).not.toBeInTheDocument();
        expect(screen.queryByText('Members')).not.toBeInTheDocument();
    });

    it('shows details and members once their URLs are granted', async () => {
        renderView(testNamespace({ detailsUrl: '/details', membersUrl: '/members' }));

        expect(await screen.findByText('Details')).toBeInTheDocument();
        expect(await screen.findByText('Members')).toBeInTheDocument();
    });

    it('renders the actions the host page injects, before the public-page link', async () => {
        renderView(testNamespace(), { headerActions: <button>Change Namespace</button> });

        expect(await screen.findByText('Change Namespace')).toBeInTheDocument();
        expect(screen.getByText('View public page')).toBeInTheDocument();
    });

    it('warns that an unverified namespace has to be claimed', async () => {
        renderView(testNamespace({ verified: false }), { showClaimAction: true });

        expect(await screen.findByText('Namespace not verified')).toBeInTheDocument();
        expect(screen.getByText(/proves you own it/)).toBeInTheDocument();
    });

    it('leaves the claim action to the publisher surface', async () => {
        renderView(testNamespace({ verified: false }));

        expect(await screen.findByText('Namespace not verified')).toBeInTheDocument();
        expect(screen.queryByText('Claim ownership')).not.toBeInTheDocument();
    });

    it('stays quiet about verification when the namespace is verified', async () => {
        renderView(testNamespace({ verified: true }), { showClaimAction: true });

        expect(await screen.findByText('Extensions')).toBeInTheDocument();
        expect(screen.queryByText('Namespace not verified')).not.toBeInTheDocument();
    });

    it('points the extension cards at the route prefix the host supplies', async () => {
        renderView(testNamespace({ extensions: { bar: 'https://registry.test/foo/bar' } }), {
            extensionRoutePrefix: '/admin-dashboard/extension'
        });

        expect(await screen.findByLabelText('bar')).toHaveAttribute('href', '/admin-dashboard/extension/foo/bar');
    });

    it('loads each extension through the query the host injects', async () => {
        const adminFetch = vi.fn().mockResolvedValue(extension('bar'));
        const { getExtensionDetail } = renderView(
            testNamespace({ extensions: { bar: 'https://registry.test/foo/bar' } }),
            {
                extensionQuery: entry => ({
                    queryKey: ['admin', 'extension', 'foo', entry.name],
                    queryFn: ({ signal }) => adminFetch(signal, entry)
                })
            }
        );

        await waitFor(() =>
            expect(adminFetch).toHaveBeenCalledWith(expect.any(AbortSignal), {
                name: 'bar',
                url: 'https://registry.test/foo/bar'
            })
        );
        expect(getExtensionDetail).not.toHaveBeenCalled();
    });

    it('falls back to the public registry endpoint when no query is injected', async () => {
        const { getExtensionDetail } = renderView(
            testNamespace({ extensions: { bar: 'https://registry.test/foo/bar' } })
        );

        await waitFor(() =>
            expect(getExtensionDetail).toHaveBeenCalledWith(expect.any(AbortSignal), 'https://registry.test/foo/bar')
        );
    });
});
