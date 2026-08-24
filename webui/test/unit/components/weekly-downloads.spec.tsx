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
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../support/test-providers';
import { WeeklyDownloads } from '../../../src/pages/extension-detail/weekly-downloads';
import { DownloadSeriesPoint, Extension, RegistryVersion } from '../../../src/extension-registry-types';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';

// The real chart pulls in SVG measurement APIs jsdom lacks; stub it so the test exercises the
// component's own logic (gating, the headline readout, and the series it feeds the chart). The
// buttons stand in for pointer movement along the axis, which is what the real chart reports
// through onHighlightedAxisChange.
vi.mock('@mui/x-charts/SparkLineChart', () => ({
    SparkLineChart: ({
        data,
        onHighlightedAxisChange
    }: {
        data: number[];
        onHighlightedAxisChange?: (items: { axisId: string; dataIndex: number }[]) => void;
    }) => (
        <div data-testid='sparkline' data-length={data.length}>
            {data.map((_, index) => (
                <button
                    key={index}
                    aria-label={`hover ${index}`}
                    onClick={() => onHighlightedAxisChange?.([{ axisId: 'x', dataIndex: index }])}
                />
            ))}
            <button aria-label='hover out' onClick={() => onHighlightedAxisChange?.([])} />
        </div>
    )
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

// Two whole weeks, 1..14 downloads per day: week 0 covers Jan 1-7 (28) and week 1, the latest,
// covers Jan 8-14 (77).
const ascending = points(Array.from({ length: 14 }, (_, i) => i + 1));

describe('WeeklyDownloads', () => {
    it('shows the last-7-days total and the trailing-week trend when analytics is enabled', async () => {
        // 14 daily points of 1000 → two whole weeks of 7000 each
        const service = serviceReturning(points(Array(14).fill(1000)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        expect(await screen.findByText((7000).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toHaveAttribute('data-length', '2');
        expect(service.getExtensionDownloadSeries).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ namespace: 'redhat', name: 'java', interval: 'day' })
        );
    });

    it('headlines the last week and labels the period it covers', async () => {
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(ascending), version: analyticsEnabled }
        });

        expect(await screen.findByText((77).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/Jan 8.*Jan 14, 2026/)).toBeInTheDocument();
    });

    it('reads out the hovered week, and returns to the last week when the pointer leaves', async () => {
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(ascending), version: analyticsEnabled }
        });
        await screen.findByText((77).toLocaleString());

        await userEvent.click(screen.getByRole('button', { name: 'hover 0' }));

        expect(screen.getByText((28).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/Jan 1.*Jan 7, 2026/)).toBeInTheDocument();
        expect(screen.queryByText((77).toLocaleString())).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'hover out' }));

        expect(screen.getByText((77).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/Jan 8.*Jan 14, 2026/)).toBeInTheDocument();
    });

    it('reserves headline width for the busiest week, so the sparkline does not resize on hover', async () => {
        // a quiet week of 7 then a busy one of 12,257,000 (ten chars with separators)
        const uneven = points([...Array(7).fill(1), ...Array(7).fill(1751000)]);
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(uneven), version: analyticsEnabled }
        });

        // asserted on the style attribute: jsdom drops `ch` from the computed style
        const reserved = `min-width: ${(12257000).toLocaleString().length}ch`;
        const headline = await screen.findByText((12257000).toLocaleString());
        expect(headline.getAttribute('style')).toContain(reserved);

        // the reservation is unchanged while the single-digit week is being read out
        await userEvent.click(screen.getByRole('button', { name: 'hover 0' }));
        expect(screen.getByText('7').getAttribute('style')).toContain(reserved);
    });

    it('shows a skeleton while the first request is in flight, then the figures', async () => {
        let resolve!: (value: { points: DownloadSeriesPoint[] }) => void;
        const service = {
            getExtensionDownloadSeries: vi.fn().mockReturnValue(new Promise(done => (resolve = done)))
        } as unknown as ExtensionRegistryService;
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        // the card's shell is already there, so the sidebar does not shift when the data lands
        expect(screen.getByRole('status', { name: 'Loading weekly downloads' })).toBeInTheDocument();
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        expect(screen.queryByTestId('sparkline')).not.toBeInTheDocument();

        resolve({ points: ascending });

        expect(await screen.findByText((77).toLocaleString())).toBeInTheDocument();
        expect(screen.queryByRole('status')).not.toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toBeInTheDocument();
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

        // the skeleton shows first, so wait for the card to settle on rendering nothing
        await waitFor(() => expect(screen.queryByText(/weekly downloads/i)).not.toBeInTheDocument());
        expect(service.getExtensionDownloadSeries).toHaveBeenCalled();
    });
});
