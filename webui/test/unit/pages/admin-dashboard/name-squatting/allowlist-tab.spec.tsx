/******************************************************************************
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
 *****************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import type { NameSquattingAllow, NameSquattingExtension } from '../../../../../src/extension-registry-types';
import { AllowlistTab } from '../../../../../src/pages/admin-dashboard/name-squatting/allowlist-tab';
import { renderWithProviders } from '../../../support/test-providers';

const ext = (namespace: string, name: string): NameSquattingExtension => ({
    namespace,
    name,
    displayName: name,
    downloadCount: 0,
    activeVersionCount: 1,
    active: true
});

const allow = (id: number, other: string, renamed = false): NameSquattingAllow => ({
    id,
    other: ext(other, 'snippets'),
    field: 'DISPLAY_NAME',
    similarity: 0.7,
    value: 'React Snippets',
    otherValue: 'React Snippets Pro',
    renamed,
    reason: id === 1 ? 'Generic snippet pack names' : undefined,
    decidedBy: 'a.rivera',
    decidedAt: '2026-10-01T00:00:00Z'
});

const mountTab = (admin: Record<string, unknown>, route = '/') => {
    renderWithProviders(<AllowlistTab />, {
        route,
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

const allowlist = {
    groups: [
        { extension: ext('snippets-co', 'react-snippets'), allows: [allow(1, 'rsnip'), allow(2, 'es7-tools')] },
        { extension: ext('prettier-dev', 'prettier-eslint'), allows: [allow(3, 'esbenp', true)] }
    ],
    page: 0,
    hasMore: false
};

describe('AllowlistTab', () => {
    it('lists the pairs of an allowed extension once it is expanded', async () => {
        const user = userEvent.setup();
        mountTab({ getNameSquattingAllowlist: vi.fn().mockResolvedValue(allowlist) });

        await user.click(await screen.findByRole('button', { name: /show the allowed pairs of snippets-co/i }));

        expect(screen.getByText('rsnip.snippets')).toBeInTheDocument();
        expect(screen.getByText('Generic snippet pack names')).toBeInTheDocument();
        expect(screen.getByText('No reason given')).toBeInTheDocument();
    });

    it('narrows to the extensions with a pair renamed since it was allowed', async () => {
        const user = userEvent.setup();
        mountTab({ getNameSquattingAllowlist: vi.fn().mockResolvedValue(allowlist) });
        await screen.findByText('snippets-co.react-snippets');

        await user.click(screen.getByLabelText('Names changed since allowed'));

        expect(screen.queryByText('snippets-co.react-snippets')).not.toBeInTheDocument();
        expect(screen.getByText('prettier-dev.prettier-eslint')).toBeInTheDocument();
    });

    it('removes every allow of an extension with "Remove all"', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingAllowlist: vi.fn().mockResolvedValue(allowlist),
            removeNameSquattingAllow: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Remove all' }));
        await user.click(screen.getByRole('button', { name: 'Remove 2 allows' }));

        await waitFor(() => expect(admin.removeNameSquattingAllow).toHaveBeenCalledTimes(2));
        expect(admin.removeNameSquattingAllow).toHaveBeenCalledWith(1);
        expect(admin.removeNameSquattingAllow).toHaveBeenCalledWith(2);
    });

    // Pairs removed before a failure are gone on the server, so the list has to be refetched anyway.
    it('reports a failed removal in the dialog and refreshes the list', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingAllowlist: vi.fn().mockResolvedValue(allowlist),
            removeNameSquattingAllow: vi
                .fn()
                .mockResolvedValueOnce({ success: 'ok' })
                .mockRejectedValueOnce({ error: 'Allowed pair not found' })
        });

        await user.click(await screen.findByRole('button', { name: 'Remove all' }));
        await user.click(screen.getByRole('button', { name: 'Remove 2 allows' }));

        expect(await screen.findByText(/allowed pair not found/i)).toBeInTheDocument();
        await waitFor(() => expect(admin.getNameSquattingAllowlist).toHaveBeenCalledTimes(2));
    });

    it('lists the allowed namespace pairs once namespaces are picked, and removes the one asked', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingAllowlist: vi.fn().mockResolvedValue(allowlist),
            getNameSquattingNamespaceAllowlist: vi.fn().mockResolvedValue({
                allows: [
                    {
                        id: 7,
                        namespace: 'redhaat',
                        otherNamespace: 'redhat',
                        similarity: 0.5,
                        reason: 'Same organization',
                        decidedBy: 'a.rivera',
                        decidedAt: '2026-10-01T00:00:00Z'
                    }
                ],
                page: 0,
                hasMore: false
            }),
            removeNameSquattingAllow: vi.fn().mockResolvedValue({ success: 'ok' })
        });
        await screen.findByText('snippets-co.react-snippets');

        await user.click(screen.getByRole('button', { name: 'Namespaces' }));
        expect(await screen.findByText('Same organization')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Remove' }));
        await user.click(screen.getByRole('button', { name: 'Remove allow' }));

        await waitFor(() => expect(admin.removeNameSquattingAllow).toHaveBeenCalledWith(7));
        await waitFor(() => expect(admin.getNameSquattingNamespaceAllowlist).toHaveBeenCalledTimes(2));
    });

    it('opens the namespace allowlist its link names', async () => {
        mountTab(
            {
                getNameSquattingNamespaceAllowlist: vi.fn().mockResolvedValue({ allows: [], page: 0, hasMore: false })
            },
            '/?tab=allowlist&kind=namespaces'
        );

        expect(await screen.findByText('No allowed namespaces yet')).toBeInTheDocument();
    });
});
