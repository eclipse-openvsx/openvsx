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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1_83 drops the Eclipse columns of user_data, but only once whoever uses them has moved the data out.
 */
class DropEclipseUserDataColumnsMigrationTest extends AbstractPostgresContainerTest {

    @Test
    void refusesToDropColumnsThatStillHoldDataAndDropsThemOnceEmpty() throws SQLException {
        var name = "drop_eclipse_columns";
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
                .locations("classpath:db/migration").target("1.82").load().migrate();

        try (
                var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()
        ) {
            statement.execute("""
                    INSERT INTO user_data (id, login_name, provider, eclipse_person_id)
                    VALUES (1, 'amy', 'github', 'p1')""");
            var latest = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").load();

            assertThatThrownBy(latest::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("still hold data");
            assertThat(columns(statement)).isEqualTo(2);

            statement.execute("UPDATE user_data SET eclipse_person_id = NULL");
            latest.migrate();
            assertThat(columns(statement)).isZero();
        }
    }

    private static int columns(java.sql.Statement statement) throws SQLException {
        try (var rows = statement.executeQuery("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'user_data' AND column_name LIKE 'eclipse%'""")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
