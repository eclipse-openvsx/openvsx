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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

/**
 * Query facade over a {@link DownloadAnalyticsRepository}: aligns ranges to UTC buckets, returns
 * dense zero-filled series, marks trailing buckets that may still change as partial, and caches
 * settled sub-ranges (data older than the settling margin never changes).
 */
public class DownloadAnalyticsService {

    private final DownloadAnalyticsRepository repository;
    private final Duration settlingMargin;
    private final Clock clock;

    private final Cache<DownloadSeriesRequest, List<DownloadSeriesRow>> settledCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public DownloadAnalyticsService(DownloadAnalyticsRepository repository, Duration settlingMargin, Clock clock) {
        this.repository = repository;
        this.settlingMargin = settlingMargin;
        this.clock = clock;
    }

    /**
     * Returns the dense, zero-filled download series for the given request, ordered by bucket
     * start and group. The range is aligned outwards to full UTC buckets.
     */
    public List<DownloadSeriesPoint> getSeries(DownloadSeriesRequest request) {
        var now = clock.instant();
        var interval = request.interval();
        var from = truncate(request.from(), interval).toInstant();
        var to = alignUp(request.to(), interval);
        var aligned = new DownloadSeriesRequest(request.extensionIds(), from, to, interval, request.groupBy());

        var settledEnd = truncate(now.minus(settlingMargin), interval).toInstant();
        List<DownloadSeriesRow> rows;
        if (!to.isAfter(settledEnd)) {
            rows = settledCache.get(aligned, repository::findSeries);
        } else if (from.isBefore(settledEnd)) {
            var settled = new DownloadSeriesRequest(
                    request.extensionIds(),
                    from,
                    settledEnd,
                    interval,
                    request.groupBy());
            var live = new DownloadSeriesRequest(request.extensionIds(), settledEnd, to, interval, request.groupBy());
            rows = Stream
                    .concat(
                            settledCache.get(settled, repository::findSeries).stream(),
                            repository.findSeries(live).stream())
                    .toList();
        } else {
            rows = repository.findSeries(aligned);
        }

        return zeroFill(aligned, rows, now);
    }

    private List<DownloadSeriesPoint> zeroFill(
            DownloadSeriesRequest request,
            List<DownloadSeriesRow> rows,
            Instant now
    ) {
        var interval = request.interval();
        var groups = rows.stream()
                .map(DownloadSeriesRow::group)
                .distinct()
                .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
                .toList();
        if (groups.isEmpty()) {
            groups = Collections.singletonList(null);
        }

        var counts = rows.stream().collect(
                Collectors.toMap(row -> new BucketKey(row.bucketStart(), row.group()), DownloadSeriesRow::count));

        var points = new ArrayList<DownloadSeriesPoint>();
        for (var bucket = truncate(request.from(), interval); bucket.toInstant()
                .isBefore(request.to()); bucket = next(bucket, interval)) {
            var bucketEnd = next(bucket, interval).toInstant();
            var partial = bucketEnd.plus(settlingMargin).isAfter(now);
            for (var group : groups) {
                var count = counts.getOrDefault(new BucketKey(bucket.toInstant(), group), 0L);
                points.add(new DownloadSeriesPoint(bucket.toInstant(), group, count, partial));
            }
        }

        return points;
    }

    private record BucketKey(Instant bucketStart, @Nullable String group) {}

    private ZonedDateTime truncate(Instant instant, DownloadSeriesInterval interval) {
        var day = instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        return switch (interval) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.with(TemporalAdjusters.firstDayOfMonth());
        };
    }

    private Instant alignUp(Instant instant, DownloadSeriesInterval interval) {
        var truncated = truncate(instant, interval);
        return truncated.toInstant().equals(instant)
                ? instant
                : next(truncated, interval).toInstant();
    }

    private ZonedDateTime next(ZonedDateTime bucket, DownloadSeriesInterval interval) {
        return switch (interval) {
            case DAY -> bucket.plusDays(1);
            case WEEK -> bucket.plusWeeks(1);
            case MONTH -> bucket.plusMonths(1);
        };
    }
}
