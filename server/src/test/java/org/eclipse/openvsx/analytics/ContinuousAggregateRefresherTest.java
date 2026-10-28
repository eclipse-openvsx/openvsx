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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractTimeseriesContainerTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a continuous aggregate of its own, without refresh policies, so whatever is
 * materialized was materialized by the refresher; sharing no tables, it needs no {@code @Isolated}.
 */
@SpringBootTest
class ContinuousAggregateRefresherTest extends AbstractTimeseriesContainerTest {

    private static final String AGGREGATE = "refresher_test_daily";

    @Autowired
    @Qualifier("timeseriesDsl")
    DSLContext dsl;

    @BeforeEach
    void createAggregate() {
        dsl.execute("CREATE TABLE refresher_test_event (time TIMESTAMPTZ NOT NULL, count INTEGER NOT NULL)");
        dsl.execute("SELECT create_hypertable('refresher_test_event', by_range('time', INTERVAL '7 days'))");
        dsl.execute("""
                CREATE MATERIALIZED VIEW refresher_test_daily
                WITH (timescaledb.continuous, timescaledb.materialized_only = true) AS
                SELECT time_bucket('1 day', time) AS day, SUM(count) AS downloads
                FROM refresher_test_event
                GROUP BY time_bucket('1 day', time)
                WITH NO DATA""");
        dsl.execute("""
                INSERT INTO refresher_test_event (time, count) VALUES
                ('2026-02-08T12:00:00Z', 1),
                ('2026-02-09T13:45:00Z', 1),
                ('2026-02-10T23:00:00Z', 1),
                ('2026-02-11T01:00:00Z', 1)""");
    }

    @AfterEach
    void dropAggregate() {
        dsl.execute("DROP MATERIALIZED VIEW IF EXISTS refresher_test_daily");
        dsl.execute("DROP TABLE IF EXISTS refresher_test_event");
    }

    @Test
    void refreshesOverTheDayAlignedWindowCoveringTheRange() {
        refresher(AGGREGATE).refresh(Instant.parse("2026-02-09T13:45:00Z"), Instant.parse("2026-02-10T01:00:00Z"));

        // start truncated to the day, end extended to include the whole day 'to' falls in
        assertThat(materializedDays()).containsExactly(
                Instant.parse("2026-02-09T00:00:00Z"),
                Instant.parse("2026-02-10T00:00:00Z"));
    }

    @Test
    void refreshesTheWholeAggregateWhenTheRangeIsOpen() {
        refresher(AGGREGATE).refresh(null, null);

        assertThat(materializedDays()).containsExactly(
                Instant.parse("2026-02-08T00:00:00Z"),
                Instant.parse("2026-02-09T00:00:00Z"),
                Instant.parse("2026-02-10T00:00:00Z"),
                Instant.parse("2026-02-11T00:00:00Z"));
    }

    @Test
    void leavesANullBoundOpen() {
        refresher(AGGREGATE).refresh(Instant.parse("2026-02-10T05:00:00Z"), null);

        assertThat(materializedDays()).containsExactly(
                Instant.parse("2026-02-10T00:00:00Z"),
                Instant.parse("2026-02-11T00:00:00Z"));
    }

    @Test
    void passesTheNameAsALiteral() {
        // concatenated into the statement, this would close the literal and comment out the rest
        var refresher = refresher(AGGREGATE + "', null, null); --");

        assertThatThrownBy(() -> refresher.refresh(null, null)).isInstanceOf(DataAccessException.class);
    }

    private ContinuousAggregateRefresher refresher(String... names) {
        return new ContinuousAggregateRefresher(dsl, List.of(() -> List.of(names)));
    }

    private List<Instant> materializedDays() {
        var day = DSL.field(DSL.name("day"), SQLDataType.TIMESTAMPWITHTIMEZONE);
        return dsl.select(day)
                .from(DSL.table(DSL.name(AGGREGATE)))
                .orderBy(day)
                .fetch(Record1::value1)
                .stream()
                .map(OffsetDateTime::toInstant)
                .toList();
    }
}
