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

import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../support/test-providers';
import { WeeklyDownloads } from '../../../src/pages/extension-detail/weekly-downloads';
import { DownloadSeriesPoint, Extension, RegistryVersion } from '../../../src/extension-registry-types';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';

// The real chart pulls in SVG measurement APIs jsdom lacks; stub it so the test exercises
// the component's own logic (gating, the headline number, and the series it feeds the chart).
vi.mock('@mui/x-charts/SparkLineChart', () => ({
    SparkLineChart: ({ data }: { data: number[] }) => <div data-testid='sparkline' data-length={data.length} />
}));

const extension = { namespace: 'redhat', name: 'java' } as unknown as Extension;
const analyticsEnabled: RegistryVersion = { version: '1.0.0', analyticsEnabled: true };

function points(counts: number[]): DownloadSeriesPoint[] {
    return counts.map((count, i) => ({ t: `2026-01-${String(i + 1).padStart(2, '0')}`, count }));
}

function serviceReturning(series: DownloadSeriesPoint[]): ExtensionRegistryService {
    return {
        getExtensionDownloadSeries: vi.fn().mockResolvedValue({ points: series })
    } as unknown as ExtensionRegistryService;
}

describe('WeeklyDownloads', () => {
    it('shows the last-7-days total and the trailing-week trend when analytics is enabled', async () => {
        // 14 daily points of 1000 → every trailing-7-day sum is 7000; rolling length = 14 - 6 = 8.
        const service = serviceReturning(points(Array(14).fill(1000)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        expect(await screen.findByText((7000).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toHaveAttribute('data-length', '8');
        expect(service.getExtensionDownloadSeries).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ namespace: 'redhat', name: 'java', interval: 'day' })
        );
    });

    it('renders nothing (and never calls the endpoint) when analytics is disabled', () => {
        const service = serviceReturning(points(Array(14).fill(1)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: { version: '1.0.0', analyticsEnabled: false } }
        });

        expect(screen.queryByText(/weekly downloads/i)).not.toBeInTheDocument();
        expect(service.getExtensionDownloadSeries).not.toHaveBeenCalled();
    });

    it('renders nothing when the extension has no downloads in the window', async () => {
        const service = serviceReturning(points(Array(14).fill(0)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        await waitFor(() => expect(service.getExtensionDownloadSeries).toHaveBeenCalled());
        expect(screen.queryByText(/weekly downloads/i)).not.toBeInTheDocument();
    });
});
