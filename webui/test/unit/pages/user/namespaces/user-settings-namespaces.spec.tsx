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
import { renderWithProviders } from '../../../support/test-providers';
import { disabledStatus, testUser } from '../../../support/trusted-publishing';
import { namespaceDetails, settingsServiceStub, testNamespace } from '../../../support/user-settings';
import { UserSettingsNamespaces } from '../../../../../src/pages/user/namespaces/user-settings-namespaces';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { Namespace } from '../../../../../src/extension-registry-types';
import { PageSettings } from '../../../../../src/page-settings';

const pageSettings = {
    urls: { namespaceAccessInfo: 'https://docs.test/claim', extensionDefaultIcon: '/icon.png' }
} as PageSettings;

function renderNamespaces(namespaces: Namespace[], selectedName?: string) {
    const { service } = settingsServiceStub({ status: disabledStatus, namespaces });
    const full = {
        ...service,
        getNamespaceDetails: vi.fn().mockResolvedValue(namespaceDetails()),
        getNamespaceMembers: vi.fn().mockResolvedValue({ namespaceMemberships: [] }),
        getExtensionDetail: vi.fn(),
        getExtension: vi.fn().mockResolvedValue({ name: 'bar', namespace: 'redhat', files: {} }),
        getExtensionIcon: vi.fn().mockResolvedValue(null)
    };
    renderWithProviders(<UserSettingsNamespaces selectedName={selectedName} />, {
        mainContext: { service: full as unknown as ExtensionRegistryService, user: testUser, pageSettings }
    });
    return full;
}

describe('UserSettingsNamespaces', () => {
    it('points at the claiming docs when the user has no namespace', async () => {
        renderNamespaces([]);

        expect(await screen.findByText(/No namespaces available/)).toBeInTheDocument();
        expect(screen.getByText('here')).toHaveAttribute('href', 'https://docs.test/claim');
        expect(screen.getByText('Create namespace')).toBeInTheDocument();
    });

    it('opens the namespace named by the route', async () => {
        renderNamespaces([testNamespace({ name: 'redhat' }), testNamespace({ name: 'acme' })], 'acme');

        expect(await screen.findByRole('heading', { name: 'acme' })).toBeInTheDocument();
    });

    it('reads each extension through the user endpoint, which keeps inactive and deleted ones', async () => {
        const namespace = testNamespace({
            name: 'redhat',
            extensions: { bar: 'https://registry.test/redhat/bar' }
        });
        const { getExtension, getExtensionDetail } = renderNamespaces([namespace], 'redhat');

        await waitFor(() => expect(getExtension).toHaveBeenCalledWith(expect.any(AbortSignal), 'redhat', 'bar'));
        // The public endpoint would hide a soft-deleted or deactivated extension.
        expect(getExtensionDetail).not.toHaveBeenCalled();
    });

    it('falls back to the first namespace when the route names none', async () => {
        renderNamespaces([testNamespace({ name: 'redhat' }), testNamespace({ name: 'acme' })]);

        expect(await screen.findByRole('heading', { name: 'redhat' })).toBeInTheDocument();
    });

    it('hides its own detail heading while no namespace has loaded', async () => {
        renderNamespaces([]);

        expect(await screen.findByText(/No namespaces available/)).toBeInTheDocument();
        expect(screen.queryByText('View public page')).not.toBeInTheDocument();
    });
});
