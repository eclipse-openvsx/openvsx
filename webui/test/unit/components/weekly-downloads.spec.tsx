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
import { screen } from '@testing-library/react';
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
        baseline,
        yAxis,
        onHighlightedAxisChange
    }: {
        data: number[];
        baseline?: number | 'min' | 'max';
        yAxis?: { domainLimit?: (min: number, max: number) => { min: number; max: number } };
        onHighlightedAxisChange?: (items: { axisId: string; dataIndex: number }[]) => void;
    }) => (
        <div
            data-testid='sparkline'
            data-length={data.length}
            data-baseline={String(baseline)}
            // exercised rather than echoed: the floor has to be zero whatever the busiest week is
            data-domain-min={String(yAxis?.domainLimit?.(0, 1200).min)}
            // an all-zero series must still get a positive top, or its flat line has no baseline to sit on
            data-domain-max-zero={String(yAxis?.domainLimit?.(0, 0).max)}>
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

function serviceRejecting(): ExtensionRegistryService {
    return {
        getExtensionDownloadSeries: vi.fn().mockRejectedValue(new Error('analytics endpoint unavailable'))
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

    it('frames the area from zero, so a steady series is not drawn as a climbing one', async () => {
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(ascending), version: analyticsEnabled }
        });

        // filling from the quietest week instead would make any series climb out of nothing, which
        // is what a cumulative chart looks like
        const chart = await screen.findByTestId('sparkline');
        expect(chart).toHaveAttribute('data-baseline', '0');
        expect(chart).toHaveAttribute('data-domain-min', '0');
    });

    it('says what the figure counts, so the number is not one of two unlabelled download counts', async () => {
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(ascending), version: analyticsEnabled }
        });

        expect(await screen.findByText((77).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText('downloads')).toBeInTheDocument();
    });

    it('describes the curve for anyone who cannot see it, naming the per-week unit', async () => {
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(ascending), version: analyticsEnabled }
        });
        await screen.findByText((77).toLocaleString());

        // week 0 is 28 and week 1 is 77; "per week" is in it because the shape of a filled curve
        // is the thing that would otherwise be read as a running total
        expect(
            screen.getByRole('img', { name: 'Downloads per week over the last 2 weeks, between 28 and 77 per week' })
        ).toBeInTheDocument();
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

    it('reserves width for the unit label, so the sparkline does not resize between singular and plural counts', async () => {
        // week 0 is a single download, week 1 (the default) is many
        const oneThenMany = points([1, 0, 0, 0, 0, 0, 0, ...Array(7).fill(80)]);
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service: serviceReturning(oneThenMany), version: analyticsEnabled }
        });

        // asserted on the style attribute: jsdom drops `ch` from the computed style
        const reserved = 'min-width: 9ch';
        const plural = await screen.findByText('downloads');
        expect(plural.getAttribute('style')).toContain(reserved);

        // the reservation is unchanged while the singular week is being read out
        await userEvent.click(screen.getByRole('button', { name: 'hover 0' }));
        expect(screen.getByText('download').getAttribute('style')).toContain(reserved);
    });

    it('draws the chart at a flat zero while loading, holding the figure until the series lands', async () => {
        let resolve!: (value: { points: DownloadSeriesPoint[] }) => void;
        const service = {
            getExtensionDownloadSeries: vi.fn().mockReturnValue(new Promise(done => (resolve = done)))
        } as unknown as ExtensionRegistryService;
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        // the chart is drawn from first paint, a flat two-point zero, while the figure is still a placeholder
        expect(screen.getByRole('img', { name: 'Weekly downloads, loading' })).toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toHaveAttribute('data-length', '2');
        expect(screen.queryByText((77).toLocaleString())).not.toBeInTheDocument();

        resolve({ points: ascending });

        expect(await screen.findByText((77).toLocaleString())).toBeInTheDocument();
        // the same chart, now labelled with the real range instead of "loading"
        expect(screen.getByRole('img', { name: /Downloads per week over the last 2 weeks/ })).toBeInTheDocument();
    });

    it('renders nothing (and never calls the endpoint) when analytics is disabled', () => {
        const service = serviceReturning(points(Array(14).fill(1)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: { version: '1.0.0', analyticsEnabled: false } }
        });

        expect(screen.queryByText(/weekly downloads/i)).not.toBeInTheDocument();
        expect(service.getExtensionDownloadSeries).not.toHaveBeenCalled();
    });

    it('still shows the card, headlining zero, when the extension has no downloads in the window', async () => {
        const service = serviceReturning(points(Array(14).fill(0)));
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        // waits past the skeleton for the loaded headline, which the eyebrow alone would not
        expect(await screen.findByText('0')).toBeInTheDocument();
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toBeInTheDocument();
    });

    it('draws a flat zero, not nothing, when the series is empty', async () => {
        const service = serviceReturning([]);
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        expect(await screen.findByText('0')).toBeInTheDocument();
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        // the two-point placeholder still feeds the chart, so the widget occupies its slot
        const chart = screen.getByTestId('sparkline');
        expect(chart).toHaveAttribute('data-length', '2');
        // a positive top keeps the domain non-degenerate, so the zero line rests on the baseline
        expect(chart).toHaveAttribute('data-domain-max-zero', '1');
        expect(screen.getByRole('img', { name: 'Downloads per week: no downloads yet' })).toBeInTheDocument();
    });

    it('shows the card as unavailable, not a claimed zero, when the request fails', async () => {
        const service = serviceRejecting();
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        // a failed request is not the same claim as "no downloads yet": it says nothing was learned
        expect(await screen.findByRole('img', { name: 'Weekly downloads unavailable' })).toBeInTheDocument();
        expect(screen.getByText('—')).toBeInTheDocument();
        expect(screen.getByText('unavailable')).toBeInTheDocument();
        expect(screen.queryByText('0')).not.toBeInTheDocument();
        // the card, and its placeholder chart, stay in place rather than disappearing
        expect(screen.getByText(/weekly downloads/i)).toBeInTheDocument();
        expect(screen.getByTestId('sparkline')).toHaveAttribute('data-length', '2');
    });

    it('does not carry a hover picked up on the loading placeholder into the real series', async () => {
        let resolve!: (value: { points: DownloadSeriesPoint[] }) => void;
        const service = {
            getExtensionDownloadSeries: vi.fn().mockReturnValue(new Promise(done => (resolve = done)))
        } as unknown as ExtensionRegistryService;
        renderWithProviders(<WeeklyDownloads extension={extension} />, {
            mainContext: { service, version: analyticsEnabled }
        });

        // the placeholder's two points are indices 0 and 1, both valid indices into the real
        // 2-week series that lands below - so a stale hover here would go unnoticed by index
        // bounds alone
        await userEvent.click(screen.getByRole('button', { name: 'hover 0' }));

        resolve({ points: ascending });

        // headlines the latest week (77, Jan 8-14), not the index hovered on the placeholder
        expect(await screen.findByText((77).toLocaleString())).toBeInTheDocument();
        expect(screen.getByText(/Jan 8.*Jan 14, 2026/)).toBeInTheDocument();
        expect(screen.queryByText((28).toLocaleString())).not.toBeInTheDocument();
    });
});
