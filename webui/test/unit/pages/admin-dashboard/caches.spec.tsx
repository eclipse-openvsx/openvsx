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
 ********************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CachesAdmin } from '../../../../src/pages/admin-dashboard/caches/caches';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { CacheInfo } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const measured: CacheInfo = {
    managers: ['caffeineCacheManager'],
    name: 'extension.json',
    implementation: 'jcache',
    entries: 1234,
    hits: 900,
    misses: 100,
    hitRate: 0.9,
    evictions: 7
};

const unmeasurable: CacheInfo = {
    managers: ['redisCacheManager'],
    name: 'sitemap',
    implementation: 'redis'
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (caches: CacheInfo[], clearCaches = vi.fn().mockResolvedValue({ success: 'ok' })) => {
    const admin = {
        getCaches: vi.fn().mockResolvedValue({ caches }),
        clearCaches
    };
    renderWithProviders(<CachesAdmin />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('CachesAdmin', () => {
    it('shows what each cache holds and how often it is hit', async () => {
        mountPage([measured]);

        expect(await screen.findByText('extension.json')).toBeInTheDocument();
        expect(screen.getByText('caffeineCacheManager')).toBeInTheDocument();
        expect(screen.getByText('1,234')).toBeInTheDocument();
        expect(screen.getByText('900')).toBeInTheDocument();
        expect(screen.getByText('90.0%')).toBeInTheDocument();
    });

    it('distinguishes a measurement the implementation cannot report from a zero', async () => {
        // The whole point of the dash: Redis reports neither size nor statistics, and showing 0
        // would claim an empty cache that is not empty.
        mountPage([unmeasurable]);

        const row = await screen.findByRole('row', { name: /sitemap/ });
        expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
        expect(within(row).queryByText('0')).not.toBeInTheDocument();
    });

    it('clears one cache by manager and name together', async () => {
        // Name alone does not identify a cache - `settings` is registered on two managers.
        const clearCaches = vi.fn().mockResolvedValue({ success: 'ok' });
        const admin = mountPage([measured], clearCaches);

        await userEvent.click(await screen.findByRole('button', { name: 'Clear extension.json' }));

        await waitFor(() =>
            expect(clearCaches).toHaveBeenCalledWith({ manager: 'caffeineCacheManager', name: 'extension.json' })
        );
        await waitFor(() => expect(admin.getCaches).toHaveBeenCalledTimes(2));
    });

    it('clears everything when no cache is named', async () => {
        const clearCaches = vi.fn().mockResolvedValue({ success: 'ok' });
        mountPage([measured], clearCaches);

        await userEvent.click(await screen.findByRole('button', { name: /Clear all/ }));

        await waitFor(() => expect(clearCaches).toHaveBeenCalledWith(undefined));
        expect(await screen.findByText('Cleared every cache.')).toBeInTheDocument();
    });

    it('reports a failed clear instead of claiming success', async () => {
        const clearCaches = vi.fn().mockRejectedValue(new Error('nope'));
        mountPage([measured], clearCaches);

        await userEvent.click(await screen.findByRole('button', { name: 'Clear extension.json' }));

        expect(await screen.findByText(/nope/)).toBeInTheDocument();
    });

    it('says so when nothing is registered', async () => {
        mountPage([]);

        expect(await screen.findByText('No caches are registered.')).toBeInTheDocument();
    });
});
