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
package org.eclipse.openvsx.analytics.timescale;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractTimeseriesContainerTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The time-series database stands on its own: nothing reads or writes it yet, so its own migration
 * chain applying to a separate database - without disturbing the registry's - is the only thing
 * there is to verify.
 */
@SpringBootTest
class TimeseriesDatabaseTest extends AbstractTimeseriesContainerTest {

    @Autowired
    @Qualifier("timeseriesDsl")
    DSLContext timeseriesDsl;

    // defaultCandidate = false on the time-series beans means plain by-type injection still reaches
    // the registry's own DSLContext, which is what this asserts.
    @Autowired
    DSLContext registryDsl;

    @Test
    void appliesItsOwnMigrationsToTheTimeSeriesDatabase() {
        // the hypertable and the continuous aggregate the analytics schema is built on
        assertThat(namesFrom("timescaledb_information.hypertables", "hypertable_name"))
                .contains("download_event");
        assertThat(namesFrom("timescaledb_information.continuous_aggregates", "view_name"))
                .contains("download_stats_daily");
    }

    // The registry database must not gain the analytics schema. The two migration sets are siblings
    // under db/ rather than parent and child, because Flyway scans locations recursively and a child
    // would have been pulled into the registry's own chain - which would also make the registry
    // require the timescaledb extension.
    @Test
    void leavesTheRegistryDatabaseAlone() {
        var registryTables = registryDsl
                .select(DSL.field("table_name", String.class))
                .from("information_schema.tables")
                .where(DSL.field("table_schema").eq("public"))
                .fetchInto(String.class);

        assertThat(registryTables).doesNotContain("download_event", "download_stats_daily");
    }

    private List<String> namesFrom(String table, String column) {
        return timeseriesDsl.select(DSL.field(column, String.class)).from(table).fetchInto(String.class);
    }
}
