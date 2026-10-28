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
import { AnalyticsStatusPage } from '../../../../src/pages/admin-dashboard/analytics-status/analytics-status';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { AnalyticsStatus } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const available: AnalyticsStatus = {
    enabled: true,
    available: true,
    since: '2026-10-15T08:00:00Z',
    lastProbe: '2026-10-15T09:00:00Z',
    pendingFiles: 0,
    failedFiles: 3
};

const unavailable: AnalyticsStatus = {
    enabled: true,
    available: false,
    since: '2026-10-15T08:00:00Z',
    lastProbe: '2026-10-15T09:00:00Z',
    lastError: 'ConnectException: Connection refused',
    pendingFiles: 1234,
    failedFiles: 0
};

// not named render*, so the testing-library naming rule does not treat the service stub it returns
// as a render result
const mountPage = (getAnalyticsStatus: ReturnType<typeof vi.fn>) => {
    const admin = { getAnalyticsStatus };
    renderWithProviders(<AnalyticsStatusPage />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('AnalyticsStatusPage', () => {
    it('shows an available database and the file counts', async () => {
        mountPage(vi.fn().mockResolvedValue(available));

        expect(await screen.findByText('Available')).toBeInTheDocument();
        expect(screen.getByText('Available since')).toBeInTheDocument();
        expect(within(screen.getByRole('row', { name: /failed ingestion/ })).getByText('3')).toBeInTheDocument();
        expect(screen.queryByText('Last error')).not.toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('says the database is unreachable, why, and what is waiting for it', async () => {
        mountPage(vi.fn().mockResolvedValue(unavailable));

        expect(await screen.findByText('Unavailable')).toBeInTheDocument();
        expect(screen.getByText('Unavailable since')).toBeInTheDocument();
        expect(screen.getByText('ConnectException: Connection refused')).toBeInTheDocument();
        expect(
            within(screen.getByRole('row', { name: /waiting for analytics/ })).getByText('1,234')
        ).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent('cannot be reached');
    });

    it('says analytics is off instead of showing a database state', async () => {
        mountPage(vi.fn().mockResolvedValue({ enabled: false, pendingFiles: 0, failedFiles: 0 }));

        expect(await screen.findByText(/turned off/)).toBeInTheDocument();
        expect(screen.queryByText('Database')).not.toBeInTheDocument();
    });

    it('reports a request that failed', async () => {
        mountPage(vi.fn().mockRejectedValue(new Error('status endpoint failed')));

        expect(await screen.findByText(/status endpoint failed/)).toBeInTheDocument();
    });

    it('asks again when refreshed', async () => {
        const getAnalyticsStatus = vi.fn().mockResolvedValue(available);
        const admin = mountPage(getAnalyticsStatus);
        await screen.findByText('Available');

        await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));

        await waitFor(() => expect(admin.getAnalyticsStatus).toHaveBeenCalledTimes(2));
    });
});
