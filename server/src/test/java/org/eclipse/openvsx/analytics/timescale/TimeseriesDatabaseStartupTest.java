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

import java.sql.SQLTransientConnectionException;

import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.eclipse.openvsx.analytics.AnalyticsAvailability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Starting the time-series configuration without a reachable database: the registry has to come up,
 * with analytics unavailable, rather than fail its own startup over an optional feature.
 */
class TimeseriesDatabaseStartupTest {

    @Test
    void startsWithoutAReachableDatabaseAndReportsAnalyticsUnavailable() {
        new ApplicationContextRunner()
                // a bare runner has no conversion service, so Duration properties would not bind
                .withInitializer(
                        context -> context.getBeanFactory()
                                .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(TimeseriesDatabaseConfiguration.class)
                .withPropertyValues(
                        "ovsx.analytics.enabled=true",
                        "ovsx.analytics.datasource.url=jdbc:postgresql://127.0.0.1:1/none",
                        "ovsx.analytics.datasource.connection-timeout=300")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AnalyticsAvailability.class).isAvailable()).isFalse();
                });
    }

    @Test
    void migratesOnceBeforeTheFirstQueryAndNotAgain() {
        var flyway = Mockito.mock(Flyway.class);
        var dsl = Mockito.mock(DSLContext.class, Mockito.RETURNS_DEEP_STUBS);
        var probe = TimeseriesDatabaseConfiguration.probe(dsl, flyway);

        probe.run();
        probe.run();

        var order = inOrder(flyway, dsl);
        order.verify(flyway).migrate();
        order.verify(dsl, atLeastOnce()).selectOne();
        verify(flyway, times(1)).migrate();
    }

    @Test
    void doesNotQueryAndMigratesAgainAfterAFailedMigration() {
        var flyway = Mockito.mock(Flyway.class);
        var dsl = Mockito.mock(DSLContext.class, Mockito.RETURNS_DEEP_STUBS);
        doThrow(new DataAccessException("down", new SQLTransientConnectionException())).doReturn(null)
                .when(flyway)
                .migrate();
        var probe = TimeseriesDatabaseConfiguration.probe(dsl, flyway);

        assertThatThrownBy(probe::run).isInstanceOf(DataAccessException.class);
        verify(dsl, never()).selectOne();

        probe.run();
        verify(flyway, times(2)).migrate();
        verify(dsl).selectOne();
    }
}
