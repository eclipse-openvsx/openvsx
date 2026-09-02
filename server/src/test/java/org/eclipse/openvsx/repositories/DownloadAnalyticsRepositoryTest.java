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

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractTimeseriesContainerTest;
import org.eclipse.openvsx.analytics.DownloadEvent;
import org.eclipse.openvsx.analytics.DownloadSeriesGroupBy;
import org.eclipse.openvsx.analytics.DownloadSeriesInterval;
import org.eclipse.openvsx.analytics.DownloadSeriesRequest;
import org.eclipse.openvsx.analytics.DownloadSeriesRow;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DownloadAnalyticsRepositoryTest extends AbstractTimeseriesContainerTest {

    @Autowired
    DownloadAnalyticsRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    JdbcTemplate jdbc;

    JdbcTemplate registryJdbc;

    // the time-series pool is not a default autowiring candidate; only the qualifier reaches it
    @Autowired
    void initJdbc(@Qualifier("timeseriesDataSource") DataSource timeseries, DataSource registry) {
        this.jdbc = new JdbcTemplate(timeseries);
        this.registryJdbc = new JdbcTemplate(registry);
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE download_event");
    }

    @Test
    void testMigrationApplied() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM timescaledb_information.hypertables WHERE hypertable_name = 'download_event'",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM timescaledb_information.continuous_aggregates WHERE view_name = 'download_stats_daily'",
                        Integer.class));
        // the time-series database has its own migration chain, starting over at version 1
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success",
                        Integer.class));
    }

    @Test
    void testAnalyticsSchemaIsAbsentFromRegistryDatabase() {
        assertNull(registryJdbc.queryForObject("SELECT to_regclass('download_event')::text", String.class));
        assertNull(registryJdbc.queryForObject("SELECT to_regclass('download_stats_daily')::text", String.class));
    }

    @Test
    void testSaveBatches() {
        var events = IntStream.range(0, 1500)
                .mapToObj(
                        i -> event(
                                Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i * 3600L),
                                1L,
                                "1.0.0",
                                "US",
                                2))
                .toList();
        repository.save(events);

        assertEquals(1500, jdbc.queryForObject("SELECT COUNT(*) FROM download_event", Integer.class));
        assertEquals(
                1500,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM download_event WHERE extension_version_id = 100",
                        Integer.class));
        assertEquals(3000, jdbc.queryForObject("SELECT SUM(count) FROM download_event", Integer.class));
        // the raw client ip and user agent are persisted as found in the logs
        assertEquals(
                1500,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM download_event WHERE ip = '9.9.9.9' AND user_agent = 'VSCode 1.90.2'",
                        Integer.class));
    }

    @Test
    void testSaveSurvivesRolledBackCallerTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            repository.save(
                    List.of(
                            event(
                                    Instant.parse("2026-07-01T10:00:00Z"),
                                    1L,
                                    "1.0.0",
                                    "US",
                                    1)));
            throw new IllegalStateException("induced failure after save");
        }));

        // the time-series database is not part of the registry transaction
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM download_event", Integer.class));
    }

    @Test
    void testSaveIsAtomicAcrossItsBatches() {
        // version is VARCHAR(255), so the 501st event lands in a second batch and fails it
        var overlongVersion = "1.0.0-" + "x".repeat(300);
        var events = IntStream.rangeClosed(0, 500)
                .mapToObj(
                        i -> event(
                                Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i * 60L),
                                1L,
                                i == 500 ? overlongVersion : "1.0.0",
                                "US",
                                1))
                .toList();

        assertThrows(DataAccessException.class, () -> repository.save(events));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM download_event", Integer.class));
    }

    @Test
    void testFindSeriesByDay() {
        repository.save(
                List.of(
                        event(Instant.parse("2026-06-30T10:00:00Z"), 1L, "1.0.0", "US", 3),
                        event(Instant.parse("2026-06-30T23:00:00Z"), 1L, "1.0.0", "DE", 2),
                        event(Instant.parse("2026-07-01T00:00:00Z"), 1L, "1.0.0", "US", 5),
                        // different extension, not requested
                        event(Instant.parse("2026-07-01T00:00:00Z"), 2L, "1.0.0", "US", 100)));

        var rows = repository.findSeries(
                DownloadSeriesRequest.of(
                        1L,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-07-02T00:00:00Z"),
                        DownloadSeriesInterval.DAY));

        assertEquals(
                List.of(
                        new DownloadSeriesRow(Instant.parse("2026-06-30T00:00:00Z"), null, 5),
                        new DownloadSeriesRow(Instant.parse("2026-07-01T00:00:00Z"), null, 5)),
                rows);
    }

    @Test
    void testFindSeriesRangeFilter() {
        repository.save(
                List.of(
                        event(Instant.parse("2026-06-28T10:00:00Z"), 1L, "1.0.0", "US", 1),
                        event(Instant.parse("2026-06-29T10:00:00Z"), 1L, "1.0.0", "US", 2),
                        event(Instant.parse("2026-06-30T10:00:00Z"), 1L, "1.0.0", "US", 4)));

        // from is inclusive, to is exclusive
        var rows = repository.findSeries(
                DownloadSeriesRequest.of(
                        1L,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-30T00:00:00Z"),
                        DownloadSeriesInterval.DAY));

        assertEquals(List.of(new DownloadSeriesRow(Instant.parse("2026-06-29T00:00:00Z"), null, 2)), rows);
    }

    @Test
    void testFindSeriesByWeekAndMonth() {
        repository.save(
                List.of(
                        // Sunday of the week starting Monday 2026-06-22, and June
                        event(Instant.parse("2026-06-28T10:00:00Z"), 1L, "1.0.0", "US", 1),
                        // Monday 2026-06-29 week, June
                        event(Instant.parse("2026-06-29T10:00:00Z"), 1L, "1.0.0", "US", 2),
                        // Wednesday of the same week, but July
                        event(Instant.parse("2026-07-01T10:00:00Z"), 1L, "1.0.0", "US", 4)));

        var weekly = repository.findSeries(
                DownloadSeriesRequest.of(
                        1L,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        DownloadSeriesInterval.WEEK));
        assertEquals(
                List.of(
                        new DownloadSeriesRow(Instant.parse("2026-06-22T00:00:00Z"), null, 1),
                        new DownloadSeriesRow(Instant.parse("2026-06-29T00:00:00Z"), null, 6)),
                weekly);

        var monthly = repository.findSeries(
                DownloadSeriesRequest.of(
                        1L,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        DownloadSeriesInterval.MONTH));
        assertEquals(
                List.of(
                        new DownloadSeriesRow(Instant.parse("2026-06-01T00:00:00Z"), null, 3),
                        new DownloadSeriesRow(Instant.parse("2026-07-01T00:00:00Z"), null, 4)),
                monthly);
    }

    @Test
    void testOutOfOrderSavesLandInCorrectBuckets() {
        repository.save(
                List.of(
                        event(Instant.parse("2026-07-02T10:00:00Z"), 1L, "1.0.0", "US", 1)));
        // a late-arriving event for an earlier day
        repository.save(
                List.of(
                        event(Instant.parse("2026-06-30T10:00:00Z"), 1L, "1.0.0", "US", 7)));

        var rows = repository.findSeries(
                DownloadSeriesRequest.of(
                        1L,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-07-03T00:00:00Z"),
                        DownloadSeriesInterval.DAY));

        assertEquals(
                List.of(
                        new DownloadSeriesRow(Instant.parse("2026-06-30T00:00:00Z"), null, 7),
                        new DownloadSeriesRow(Instant.parse("2026-07-02T00:00:00Z"), null, 1)),
                rows);
    }

    @Test
    void testFindSeriesGroupBy() {
        repository.save(
                List.of(
                        event(Instant.parse("2026-07-01T08:00:00Z"), 1L, "1.0.0", "US", 1),
                        event(Instant.parse("2026-07-01T09:00:00Z"), 1L, "2.0.0", "DE", 2),
                        event(Instant.parse("2026-07-01T10:00:00Z"), 1L, "2.0.0", null, 4)));

        var from = Instant.parse("2026-07-01T00:00:00Z");
        var to = Instant.parse("2026-07-02T00:00:00Z");

        var byVersion = repository.findSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        from,
                        to,
                        DownloadSeriesInterval.DAY,
                        DownloadSeriesGroupBy.VERSION));
        assertEquals(
                List.of(new DownloadSeriesRow(from, "1.0.0", 1), new DownloadSeriesRow(from, "2.0.0", 6)),
                byVersion);

        var byCountry = repository.findSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        from,
                        to,
                        DownloadSeriesInterval.DAY,
                        DownloadSeriesGroupBy.COUNTRY));
        assertEquals(3, byCountry.size());
        assertTrue(byCountry.contains(new DownloadSeriesRow(from, "US", 1)));
        assertTrue(byCountry.contains(new DownloadSeriesRow(from, "DE", 2)));
        assertTrue(byCountry.contains(new DownloadSeriesRow(from, null, 4)));

        var byTargetPlatform = repository.findSeries(
                new DownloadSeriesRequest(
                        List.of(1L),
                        from,
                        to,
                        DownloadSeriesInterval.DAY,
                        DownloadSeriesGroupBy.TARGET_PLATFORM));
        assertEquals(List.of(new DownloadSeriesRow(from, "universal", 7)), byTargetPlatform);
    }

    @Test
    void testFindSeriesForMultipleExtensions() {
        repository.save(
                List.of(
                        event(Instant.parse("2026-07-01T08:00:00Z"), 1L, "1.0.0", "US", 1),
                        event(Instant.parse("2026-07-01T09:00:00Z"), 2L, "1.0.0", "US", 2),
                        event(Instant.parse("2026-07-01T09:00:00Z"), 3L, "1.0.0", "US", 100)));

        var rows = repository.findSeries(
                new DownloadSeriesRequest(
                        List.of(1L, 2L),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-02T00:00:00Z"),
                        DownloadSeriesInterval.DAY,
                        DownloadSeriesGroupBy.NONE));

        assertEquals(List.of(new DownloadSeriesRow(Instant.parse("2026-07-01T00:00:00Z"), null, 3)), rows);
    }

    private DownloadEvent event(
            Instant time,
            long extensionId,
            String version,
            String country,
            int count
    ) {
        return new DownloadEvent(
                time,
                extensionId,
                extensionId * 100,
                "ns",
                "ext",
                version,
                "universal",
                country,
                "9.9.9.9",
                "VSCode 1.90.2",
                count);
    }

}
