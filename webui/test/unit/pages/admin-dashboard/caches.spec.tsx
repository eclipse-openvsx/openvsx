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
    manager: 'caffeineCacheManager',
    name: 'extension.json',
    implementation: 'jcache',
    entries: 1234,
    hits: 900,
    misses: 100,
    hitRate: 0.9,
    evictions: 7
};

const unmeasurable: CacheInfo = {
    manager: 'redisCacheManager',
    name: 'sitemap',
    implementation: 'redis'
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (
    caches: CacheInfo[],
    clearCaches = vi.fn().mockResolvedValue({ success: 'ok' }),
    statisticsEnabled = true,
    cdnPurgeEnabled = false,
    purgeCdn = vi.fn().mockResolvedValue({ success: 'ok' })
) => {
    const admin = {
        getCaches: vi.fn().mockResolvedValue({ caches, statisticsEnabled, cdnPurgeEnabled }),
        clearCaches,
        purgeCdn
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
        // Name alone does not identify a cache: two managers may each hold one of that name.
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

    it('says why the statistics are empty when counting is off', async () => {
        // Without this the empty columns read as caches that are never hit, which is the mistake the
        // whole absent-not-zero treatment exists to prevent.
        mountPage([{ ...measured, hits: undefined, misses: undefined, hitRate: undefined }], undefined, false);

        expect(await screen.findByText(/ovsx.caching.statistics.enabled/)).toBeInTheDocument();
    });

    it('does not nag about statistics when they are being collected', async () => {
        mountPage([measured]);

        await screen.findByText('extension.json');
        expect(screen.queryByText(/ovsx.caching.statistics.enabled/)).not.toBeInTheDocument();
    });

    it('says so when nothing is registered', async () => {
        mountPage([]);

        expect(await screen.findByText('No caches are registered.')).toBeInTheDocument();
    });

    // The registry purges the keys it knows about by itself; this is for a CDN holding something it
    // does not know is wrong, and there is nothing to offer where no CDN is configured.
    it('offers no CDN purge where no CDN is configured', async () => {
        mountPage([measured]);

        expect(await screen.findByText('extension.json')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Purge CDN' })).not.toBeInTheDocument();
    });

    it('drops everything the CDN holds when asked', async () => {
        const purgeCdn = vi.fn().mockResolvedValue({ success: 'ok' });
        mountPage([measured], undefined, true, true, purgeCdn);

        await userEvent.click(await screen.findByRole('button', { name: 'Purge CDN' }));

        await waitFor(() => expect(purgeCdn).toHaveBeenCalledOnce());
        expect(await screen.findByText('Purged everything the CDN holds.')).toBeInTheDocument();
    });

    it('says so when the CDN could not be purged', async () => {
        const purgeCdn = vi.fn().mockRejectedValue(new Error('Could not purge the CDN: nope'));
        mountPage([measured], undefined, true, true, purgeCdn);

        await userEvent.click(await screen.findByRole('button', { name: 'Purge CDN' }));

        expect(await screen.findByText(/Could not purge the CDN/)).toBeInTheDocument();
    });
});
