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
package org.eclipse.openvsx.analytics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadAnalyticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");
    private static final Duration SETTLING_MARGIN = Duration.ofHours(2);

    private final FakeRepository repository = new FakeRepository();
    private final DownloadAnalyticsService service = new DownloadAnalyticsService(
            repository,
            SETTLING_MARGIN,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void testDenseZeroFilledSeries() {
        repository.rows = List.of(
                new DownloadSeriesRow(Instant.parse("2026-07-11T00:00:00Z"), null, 5),
                new DownloadSeriesRow(Instant.parse("2026-07-13T00:00:00Z"), null, 2));

        var points = service.getSeries(dayRequest("2026-07-10T00:00:00Z", "2026-07-15T00:00:00Z"));

        assertEquals(5, points.size());
        assertEquals(point("2026-07-10T00:00:00Z", 0, false), points.get(0));
        assertEquals(point("2026-07-11T00:00:00Z", 5, false), points.get(1));
        assertEquals(point("2026-07-12T00:00:00Z", 0, false), points.get(2));
        assertEquals(point("2026-07-13T00:00:00Z", 2, false), points.get(3));
        assertEquals(point("2026-07-14T00:00:00Z", 0, false), points.get(4));
    }

    @Test
    void testBucketsStartAtUtcBoundaries() {
        var points = service.getSeries(dayRequest("2026-07-10T15:30:00Z", "2026-07-12T01:00:00Z"));

        assertEquals(
                List.of(
                        Instant.parse("2026-07-10T00:00:00Z"),
                        Instant.parse("2026-07-11T00:00:00Z"),
                        Instant.parse("2026-07-12T00:00:00Z")),
                points.stream().map(DownloadSeriesPoint::bucketStart).toList());
    }

    @Test
    void testTrailingPointsAreMarkedPartial() {
        var points = service.getSeries(dayRequest("2026-07-13T00:00:00Z", "2026-07-16T00:00:00Z"));

        assertEquals(3, points.size());
        // 2026-07-13 ended at 07-14T00:00; well past the settling margin
        assertFalse(points.get(0).partial());
        // 2026-07-14 ended at 07-15T00:00 + 2h margin = 07-15T02:00 <= now, settled
        assertFalse(points.get(1).partial());
        // 2026-07-15 is still running
        assertTrue(points.get(2).partial());
    }

    @Test
    void testLastCompletedDayStaysPartialWithinSettlingMargin() {
        var earlyMorning = Instant.parse("2026-07-15T01:00:00Z");
        var service = new DownloadAnalyticsService(
                repository,
                SETTLING_MARGIN,
                Clock.fixed(earlyMorning, ZoneOffset.UTC));

        var points = service.getSeries(dayRequest("2026-07-13T00:00:00Z", "2026-07-15T00:00:00Z"));

        assertFalse(points.get(0).partial());
        // 2026-07-14 ended at 07-15T00:00, but the settling margin has not passed yet
        assertTrue(points.get(1).partial());
    }

    @Test
    void testSettledRangesAreCached() {
        var request = dayRequest("2026-07-01T00:00:00Z", "2026-07-10T00:00:00Z");
        service.getSeries(request);
        service.getSeries(request);

        assertEquals(1, repository.calls.get());
    }

    @Test
    void testUnsettledTailIsNotCached() {
        // the settled part [07-13, 07-15) is cached, the live part [07-15, 07-16) is re-queried
        var request = dayRequest("2026-07-13T00:00:00Z", "2026-07-16T00:00:00Z");
        service.getSeries(request);
        assertEquals(2, repository.calls.get());
        assertEquals(
                List.of(Instant.parse("2026-07-13T00:00:00Z"), Instant.parse("2026-07-15T00:00:00Z")),
                repository.requests.stream().map(DownloadSeriesRequest::from).toList());

        service.getSeries(request);
        assertEquals(3, repository.calls.get());
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), repository.requests.get(2).from());
    }

    @Test
    void testGroupedSeriesIsZeroFilledPerGroup() {
        repository.rows = List.of(
                new DownloadSeriesRow(Instant.parse("2026-07-10T00:00:00Z"), "US", 3),
                new DownloadSeriesRow(Instant.parse("2026-07-11T00:00:00Z"), "DE", 2));

        var points = service.getSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        Instant.parse("2026-07-10T00:00:00Z"),
                        Instant.parse("2026-07-12T00:00:00Z"),
                        DownloadSeriesInterval.DAY,
                        DownloadSeriesGroupBy.COUNTRY));

        assertEquals(
                List.of(
                        point("2026-07-10T00:00:00Z", "DE", 0, false),
                        point("2026-07-10T00:00:00Z", "US", 3, false),
                        point("2026-07-11T00:00:00Z", "DE", 2, false),
                        point("2026-07-11T00:00:00Z", "US", 0, false)),
                points);
    }

    @Test
    void testWeeklyBucketsStartOnUtcMondays() {
        repository.rows = List.of(new DownloadSeriesRow(Instant.parse("2026-06-08T00:00:00Z"), null, 4));

        var points = service.getSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        Instant.parse("2026-06-03T00:00:00Z"),
                        Instant.parse("2026-06-22T00:00:00Z"),
                        DownloadSeriesInterval.WEEK,
                        DownloadSeriesGroupBy.NONE));

        // 2026-06-03 is a Wednesday; its bucket starts Monday 2026-06-01
        assertEquals(
                List.of(
                        point("2026-06-01T00:00:00Z", 0, false),
                        point("2026-06-08T00:00:00Z", 4, false),
                        point("2026-06-15T00:00:00Z", 0, false)),
                points);
    }

    @Test
    void testMonthlyBucketsStartOnFirstOfMonth() {
        repository.rows = List.of(new DownloadSeriesRow(Instant.parse("2026-06-01T00:00:00Z"), null, 9));

        var points = service.getSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        Instant.parse("2026-05-15T00:00:00Z"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        DownloadSeriesInterval.MONTH,
                        DownloadSeriesGroupBy.NONE));

        assertEquals(
                List.of(point("2026-05-01T00:00:00Z", 0, false), point("2026-06-01T00:00:00Z", 9, false)),
                points);
    }

    private DownloadSeriesRequest dayRequest(String from, String to) {
        return DownloadSeriesRequest.of(1L, Instant.parse(from), Instant.parse(to), DownloadSeriesInterval.DAY);
    }

    private DownloadSeriesPoint point(String bucketStart, long count, boolean partial) {
        return point(bucketStart, null, count, partial);
    }

    private DownloadSeriesPoint point(String bucketStart, String group, long count, boolean partial) {
        return new DownloadSeriesPoint(Instant.parse(bucketStart), group, count, partial);
    }

    /** Overrides everything the service calls, so the inherited DSLContext is never touched. */
    private static class FakeRepository extends DownloadAnalyticsRepository {
        FakeRepository() {
            super(null);
        }

        List<DownloadSeriesRow> rows = List.of();
        final AtomicInteger calls = new AtomicInteger();
        final List<DownloadSeriesRequest> requests = new ArrayList<>();

        @Override
        public void save(List<DownloadEvent> events) {
        }

        @Override
        public List<DownloadSeriesRow> findSeries(DownloadSeriesRequest request) {
            calls.incrementAndGet();
            requests.add(request);
            return rows.stream()
                    .filter(
                            row -> !row.bucketStart().isBefore(request.from())
                                    && row.bucketStart().isBefore(request.to()))
                    .toList();
        }
    }
}
