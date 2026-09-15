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
import { DateTime } from 'luxon';
import { StatisticsAdmin } from '../../../../src/pages/admin-dashboard/statistics/statistics';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { AdminStatistics } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const statistics = (overrides: Partial<AdminStatistics> = {}): AdminStatistics => ({
    year: 2026,
    month: 8,
    extensions: 1234,
    downloads: 4567,
    downloadsTotal: 89012,
    publishers: 345,
    averageReviewsPerExtension: 2.5,
    namespaceOwners: 67,
    extensionsByRating: [
        { rating: 4, extensions: 150 },
        { rating: 5, extensions: 250 }
    ],
    publishersByExtensionsPublished: [{ extensionsPublished: 1, publishers: 500 }],
    topMostActivePublishingUsers: [{ userLoginName: 'busy-bot', publishedExtensionVersions: 400 }],
    topNamespaceExtensions: [{ namespace: 'redhat', extensions: 45 }],
    topNamespaceExtensionVersions: [{ namespace: 'redhat', extensionVersions: 654 }],
    topMostDownloadedExtensions: [{ extensionIdentifier: 'redhat.java', downloads: 40086502 }],
    ...overrides
});

// not named render*, so the testing-library naming rule doesn't treat the stub it returns as a
// render result
const mountPage = (getAdminStatistics: ReturnType<typeof vi.fn> = vi.fn().mockResolvedValue(statistics())) => {
    const admin = {
        getAdminStatistics,
        getAdminStatisticsCsvUrl: (year: number, month: number) => `/admin/statistics/csv?year=${year}&month=${month}`
    };
    renderWithProviders(<StatisticsAdmin />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('StatisticsAdmin', () => {
    it('shows the headline figures for the month', async () => {
        mountPage();

        expect(await screen.findByText('1,234')).toBeInTheDocument();
        expect(screen.getByText('4,567')).toBeInTheDocument();
        expect(screen.getByText('89,012')).toBeInTheDocument();
        expect(screen.getByText('2.50')).toBeInTheDocument();
    });

    // Opens on the month in progress, which is the only month always available - a fresh registry
    // has nothing archived at all.
    it('opens on the current month and asks for it', async () => {
        const now = DateTime.utc();
        const getAdminStatistics = vi.fn().mockResolvedValue(statistics());

        mountPage(getAdminStatistics);

        await waitFor(() => expect(getAdminStatistics).toHaveBeenCalled());
        const [, year, month] = getAdminStatistics.mock.calls[0];
        expect(year).toBe(now.year);
        expect(month).toBe(now.month);
    });

    it('says the current month is still being calculated', async () => {
        mountPage();

        expect(await screen.findByText(/still in progress and is calculated on request/)).toBeInTheDocument();
    });

    it('steps back a month and refetches', async () => {
        const getAdminStatistics = vi.fn().mockResolvedValue(statistics());
        mountPage(getAdminStatistics);
        await waitFor(() => expect(getAdminStatistics).toHaveBeenCalled());

        await userEvent.click(screen.getByLabelText('Previous month'));

        const lastMonth = DateTime.utc().minus({ months: 1 });
        await waitFor(() => {
            expect(getAdminStatistics).toHaveBeenCalledWith(expect.anything(), lastMonth.year, lastMonth.month);
        });
    });

    // There is nothing past the month in progress, and the server rejects it as future.
    it('does not offer a month beyond the current one', async () => {
        mountPage();

        await waitFor(() => expect(screen.getByLabelText('Next month')).toBeDisabled());
    });

    // A month that ended without the archival job running has no data and never will, so this is a
    // normal state rather than a failure. The rejection carries the status the request layer sets,
    // since that is what tells this case apart from the one below.
    it('explains an unarchived month instead of reporting an error', async () => {
        mountPage(vi.fn().mockRejectedValue({ status: 404, message: 'Not Found' }));

        expect(await screen.findByText(/No statistics were archived for/)).toBeInTheDocument();
    });

    // Reporting a failed request as "not archived" would tell an admin the data does not exist when
    // it was never asked for successfully.
    it('reports a failed request as an error rather than as an unarchived month', async () => {
        mountPage(vi.fn().mockRejectedValue({ status: 500, message: 'Internal Server Error' }));

        expect(await screen.findByText(/could not be loaded/)).toBeInTheDocument();
        expect(screen.queryByText(/No statistics were archived for/)).not.toBeInTheDocument();
    });

    it('offers the CSV export for the shown month', async () => {
        mountPage();

        const now = DateTime.utc();
        const link = await screen.findByRole('link', { name: /Download CSV/ });
        expect(link).toHaveAttribute('href', `/admin/statistics/csv?year=${now.year}&month=${now.month}`);
    });

    it('links the breakdowns to the extensions and namespaces they name', async () => {
        mountPage();

        expect(await screen.findByRole('link', { name: 'redhat.java' })).toHaveAttribute(
            'href',
            '/extension/redhat/java'
        );
        expect(screen.getAllByRole('link', { name: 'redhat' })[0]).toHaveAttribute('href', '/namespace/redhat');
    });

    it('names the most active publishing users', async () => {
        mountPage();

        // Scoped to the row: the same figure also appears among the chart's rendered values. A row's
        // accessible name comes from its cells, so this finds it without walking up from the text.
        const row = await screen.findByRole('row', { name: /busy-bot/ });
        expect(within(row).getByText('400')).toBeInTheDocument();
    });
});
