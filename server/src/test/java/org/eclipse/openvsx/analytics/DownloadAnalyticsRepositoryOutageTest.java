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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The repository against a database that is not there: nothing listens on the port, so every
 * connection attempt is refused, which is what an outage looks like to the pool.
 */
class DownloadAnalyticsRepositoryOutageTest {

    private static final DownloadSeriesRequest REQUEST = DownloadSeriesRequest.of(
            1L,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-08T00:00:00Z"),
            DownloadSeriesInterval.DAY);

    private final AnalyticsAvailability availability = new AnalyticsAvailability(() -> {
    }, Duration.ofMinutes(10), 2);

    private final DownloadAnalyticsRepository repository = new DownloadAnalyticsRepository(
            DSL.using(
                    new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "u", "p"),
                    SQLDialect.POSTGRES),
            availability);

    @Test
    void failsFastWithoutTouchingTheDatabaseWhileUnavailable() {
        assertThat(availability.isAvailable()).isFalse();

        assertThatThrownBy(() -> repository.findSeries(REQUEST)).isInstanceOf(AnalyticsUnavailableException.class)
                .hasNoCause();
        assertThatThrownBy(() -> repository.save(List.of())).isInstanceOf(AnalyticsUnavailableException.class)
                .hasNoCause();
    }

    @Test
    void anUnreachableDatabaseSurfacesAsUnavailableAndOpensTheCircuit() {
        availability.runProbe(false);

        assertThatThrownBy(() -> repository.findSeries(REQUEST)).isInstanceOf(AnalyticsUnavailableException.class)
                .hasCauseInstanceOf(org.jooq.exception.DataAccessException.class);
        assertThat(availability.isAvailable()).isTrue();

        assertThatThrownBy(() -> repository.findSeries(REQUEST)).isInstanceOf(AnalyticsUnavailableException.class);
        assertThat(availability.isAvailable()).isFalse();
        assertThat(repository.isAvailable()).isFalse();
    }
}
