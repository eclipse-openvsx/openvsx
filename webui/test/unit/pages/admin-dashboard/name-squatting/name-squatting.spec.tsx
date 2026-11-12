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
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { NameSquatting } from '../../../../../src/pages/admin-dashboard/name-squatting/name-squatting';
import { renderWithProviders } from '../../../support/test-providers';

const service = () =>
    ({
        admin: {
            getNameSquattingCollisions: vi.fn().mockResolvedValue({ groups: [], nextOffset: 0, hasMore: false }),
            getNameSquattingAllowlist: vi.fn().mockResolvedValue({ groups: [], page: 0, hasMore: false }),
            getNameSquattingNamespacePairs: vi
                .fn()
                .mockResolvedValue({ pairs: [], total: 0, popularTotal: 0, offset: 0, hasMore: false })
        }
    }) as unknown as ExtensionRegistryService;

describe('NameSquatting', () => {
    it('opens the tab its link names', async () => {
        renderWithProviders(<NameSquatting />, { route: '/?tab=allowlist', mainContext: { service: service() } });

        expect(await screen.findByText('No allowed pairs yet')).toBeInTheDocument();
    });

    it('opens the namespaces tab its link names', async () => {
        renderWithProviders(<NameSquatting />, { route: '/?tab=namespaces', mainContext: { service: service() } });

        expect(await screen.findByText('No similar namespace names found')).toBeInTheDocument();
    });

    it('switches between the collisions and the allowlist', async () => {
        const user = userEvent.setup();
        renderWithProviders(<NameSquatting />, { mainContext: { service: service() } });
        expect(await screen.findByText('No similar names found')).toBeInTheDocument();

        await user.click(screen.getByRole('tab', { name: 'Allowlist' }));

        expect(await screen.findByText('No allowed pairs yet')).toBeInTheDocument();
    });
});
