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
package org.eclipse.openvsx.repositories;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.google.common.collect.Lists;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import org.eclipse.openvsx.analytics.DownloadAnalyticsService;
import org.eclipse.openvsx.analytics.DownloadEvent;
import org.eclipse.openvsx.analytics.DownloadSeriesGroupBy;
import org.eclipse.openvsx.analytics.DownloadSeriesInterval;
import org.eclipse.openvsx.analytics.DownloadSeriesRequest;
import org.eclipse.openvsx.analytics.DownloadSeriesRow;

/**
 * Storage for download analytics, backed by TimescaleDB: writes to the download_event hypertable
 * and reads from the download_stats_daily continuous aggregate. Both live in the separate
 * time-series database, addressed by name rather than through generated jOOQ classes (codegen runs
 * against the registry database, which does not hold these tables).
 * <p>
 * Writes run on the time-series connection pool and cannot join a caller's registry transaction:
 * one {@link #save(List)} call is one transaction of its own, atomic across its batches and
 * independent of whatever the registry does afterwards. So the extension download counter and the
 * download ingestion entry commit independently of these events.
 * <p>
 * Only constructed when {@code ovsx.analytics.enabled=true}; see {@code DownloadAnalyticsConfiguration}.
 */
public class DownloadAnalyticsRepository {

    private static final int BATCH_SIZE = 500;

    private static final Table<Record> EVENT = DSL.table(DSL.name("download_event"));
    private static final Field<OffsetDateTime> EVENT_TIME = DSL
            .field(DSL.name("download_event", "time"), OffsetDateTime.class);
    private static final Field<Long> EVENT_EXTENSION_ID = DSL
            .field(DSL.name("download_event", "extension_id"), Long.class);
    private static final Field<Long> EVENT_EXTENSION_VERSION_ID = DSL
            .field(DSL.name("download_event", "extension_version_id"), Long.class);
    private static final Field<String> EVENT_NAMESPACE = DSL
            .field(DSL.name("download_event", "namespace"), String.class);
    private static final Field<String> EVENT_EXTENSION_NAME = DSL
            .field(DSL.name("download_event", "extension_name"), String.class);
    private static final Field<String> EVENT_VERSION = DSL
            .field(DSL.name("download_event", "version"), String.class);
    private static final Field<String> EVENT_TARGET_PLATFORM = DSL
            .field(DSL.name("download_event", "target_platform"), String.class);
    private static final Field<String> EVENT_COUNTRY = DSL
            .field(DSL.name("download_event", "country"), String.class);
    private static final Field<String> EVENT_IP = DSL.field(DSL.name("download_event", "ip"), String.class);
    private static final Field<String> EVENT_USER_AGENT = DSL
            .field(DSL.name("download_event", "user_agent"), String.class);
    private static final Field<Integer> EVENT_COUNT = DSL
            .field(DSL.name("download_event", "count"), Integer.class);

    private static final Table<Record> STATS = DSL.table(DSL.name("download_stats_daily"));
    private static final Field<OffsetDateTime> STATS_DAY = DSL
            .field(DSL.name("download_stats_daily", "day"), OffsetDateTime.class);
    private static final Field<Long> STATS_EXTENSION_ID = DSL
            .field(DSL.name("download_stats_daily", "extension_id"), Long.class);
    private static final Field<String> STATS_VERSION = DSL
            .field(DSL.name("download_stats_daily", "version"), String.class);
    private static final Field<String> STATS_TARGET_PLATFORM = DSL
            .field(DSL.name("download_stats_daily", "target_platform"), String.class);
    private static final Field<String> STATS_COUNTRY = DSL
            .field(DSL.name("download_stats_daily", "country"), String.class);
    private static final Field<Long> STATS_DOWNLOADS = DSL
            .field(DSL.name("download_stats_daily", "downloads"), Long.class);

    private final DSLContext dsl;

    public DownloadAnalyticsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Persists the given events atomically: either all of them are stored or none are.
     */
    public void save(List<DownloadEvent> events) {
        dsl.transaction(configuration -> {
            for (var batch : Lists.partition(events, BATCH_SIZE)) {
                var insert = DSL.using(configuration)
                        .insertInto(
                                EVENT,
                                EVENT_TIME,
                                EVENT_EXTENSION_ID,
                                EVENT_EXTENSION_VERSION_ID,
                                EVENT_NAMESPACE,
                                EVENT_EXTENSION_NAME,
                                EVENT_VERSION,
                                EVENT_TARGET_PLATFORM,
                                EVENT_COUNTRY,
                                EVENT_IP,
                                EVENT_USER_AGENT,
                                EVENT_COUNT);
                for (var event : batch) {
                    insert = insert.values(
                            OffsetDateTime.ofInstant(event.time(), ZoneOffset.UTC),
                            event.extensionId(),
                            event.extensionVersionId(),
                            event.namespace(),
                            event.extensionName(),
                            event.version(),
                            event.targetPlatform(),
                            event.country(),
                            event.ip(),
                            event.userAgent(),
                            event.count());
                }
                insert.execute();
            }
        });
    }

    /**
     * Returns the (sparse) aggregated download series for the given request. Buckets without
     * downloads are absent; zero-filling is the {@link DownloadAnalyticsService}'s concern.
     */
    public List<DownloadSeriesRow> findSeries(DownloadSeriesRequest request) {
        var bucket = bucketField(request.interval());
        var group = groupField(request.groupBy());
        var total = DSL.sum(STATS_DOWNLOADS).cast(Long.class);

        List<Field<?>> groupByFields = request.groupBy() == DownloadSeriesGroupBy.NONE
                ? List.<Field<?>>of(bucket)
                : List.<Field<?>>of(bucket, group);
        return dsl.select(bucket, group, total)
                .from(STATS)
                .where(
                        STATS_EXTENSION_ID.in(request.extensionIds()),
                        STATS_DAY.greaterOrEqual(OffsetDateTime.ofInstant(request.from(), ZoneOffset.UTC)),
                        STATS_DAY.lessThan(OffsetDateTime.ofInstant(request.to(), ZoneOffset.UTC)))
                .groupBy(groupByFields)
                .orderBy(groupByFields)
                .fetch(record -> new DownloadSeriesRow(record.value1().toInstant(), record.value2(), record.value3()));
    }

    private Field<OffsetDateTime> bucketField(DownloadSeriesInterval interval) {
        // `day` holds UTC-aligned buckets; date_trunc must not depend on the session time zone,
        // hence the AT TIME ZONE round-trip
        return switch (interval) {
            case DAY -> STATS_DAY;
            case WEEK -> DSL.field(
                    "(date_trunc('week', {0} AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')",
                    OffsetDateTime.class,
                    STATS_DAY);
            case MONTH -> DSL.field(
                    "(date_trunc('month', {0} AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')",
                    OffsetDateTime.class,
                    STATS_DAY);
        };
    }

    private Field<String> groupField(DownloadSeriesGroupBy groupBy) {
        return switch (groupBy) {
            case NONE -> DSL.inline(null, String.class);
            case VERSION -> STATS_VERSION;
            case TARGET_PLATFORM -> STATS_TARGET_PLATFORM;
            case COUNTRY -> STATS_COUNTRY;
        };
    }
}
