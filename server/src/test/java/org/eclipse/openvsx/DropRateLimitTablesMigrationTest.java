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
package org.eclipse.openvsx;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1_84 drops the rate limit tables, but only once whoever uses them has moved the rows out.
 */
class DropRateLimitTablesMigrationTest extends AbstractPostgresContainerTest {

    private static final List<String> TABLES = List.of(
            "daily_usage_stats",
            "usage_stats",
            "rate_limit_token",
            "customer_membership",
            "customer",
            "tier");

    private static final Map<String, String> ROWS = Map.of(
            "daily_usage_stats",
            "INSERT INTO daily_usage_stats (date, total_requests, p95_requests) VALUES (now(), 1, 1)",
            "usage_stats",
            "INSERT INTO usage_stats (id, window_start, duration, count) VALUES (1, now(), 1, 1)",
            "rate_limit_token",
            "INSERT INTO rate_limit_token (active) VALUES (true)",
            "customer_membership",
            "INSERT INTO customer_membership DEFAULT VALUES",
            "customer",
            "INSERT INTO customer (id, name, state) VALUES (1, 'c', 'NORMAL')",
            "tier",
            "INSERT INTO tier (id, name, tier_type, capacity, duration, refill_strategy) VALUES (1, 't', 'FREE', 1, 1, 'GREEDY')");

    @ParameterizedTest
    @ValueSource(
        strings = { "daily_usage_stats", "usage_stats", "rate_limit_token", "customer_membership", "customer", "tier" }
    )
    void refusesToDropWhileATableHoldsARow(String table) throws SQLException {
        var url = createDatabase("drop_rate_limit_" + table);
        try (
                var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()
        ) {
            statement.execute(ROWS.get(table));

            assertThatThrownBy(latest(url)::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("still hold rows: " + table + ".");
            assertThat(count(statement, "pg_tables WHERE schemaname = 'public' AND tablename", TABLES))
                    .isEqualTo(TABLES.size());
        }
    }

    @Test
    void dropsTablesAndSequencesWhenEmpty() throws SQLException {
        var url = createDatabase("drop_rate_limit_empty");
        try (
                var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()
        ) {
            var sequences = TABLES.stream().map(t -> t + "_seq").toList();
            assertThat(count(statement, "pg_sequences WHERE schemaname = 'public' AND sequencename", sequences))
                    .isEqualTo(TABLES.size());

            latest(url).migrate();

            assertThat(count(statement, "pg_tables WHERE schemaname = 'public' AND tablename", TABLES)).isZero();
            assertThat(count(statement, "pg_sequences WHERE schemaname = 'public' AND sequencename", sequences))
                    .isZero();
        }
    }

    private static Flyway latest(String url) {
        return Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load();
    }

    /** Creates a database migrated up to V1_83 and returns its URL. */
    private static String createDatabase(String name) throws SQLException {
        try (
                var admin = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
                var statement = admin.createStatement()
        ) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        }
        var url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("1.83").load().migrate();
        return url;
    }

    private static int count(Statement statement, String catalogFilter, List<String> names) throws SQLException {
        try (
                var rows = statement.executeQuery(
                        "SELECT count(*) FROM " + catalogFilter
                                + " IN ('" + String.join("','", names) + "')")
        ) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
