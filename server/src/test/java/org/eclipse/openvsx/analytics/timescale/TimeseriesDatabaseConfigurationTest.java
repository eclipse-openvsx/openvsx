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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the startup validation of {@link TimeseriesDatabaseConfiguration}. These beans only
 * exist when {@code ovsx.analytics.enabled=true}, so every case here is a deployment that asked for
 * analytics and configured them wrongly.
 */
class TimeseriesDatabaseConfigurationTest {

    @Test
    void acceptsAUrlWithTheDefaultsForEverythingElse() {
        assertThatCode(() -> config().validateConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void rejectsAMissingUrl() {
        var config = config();
        config.url = "";

        assertThatThrownBy(config::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.datasource.url");
    }

    @Test
    void acceptsUnsetCredentials() {
        // Optional: the driver may resolve them from a URL parameter, .pgpass or IAM instead.
        var config = config();
        config.username = "";
        config.password = "";

        assertThatCode(config::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPoolThatCannotHandOutAConnection() {
        var config = config();
        config.maximumPoolSize = 0;

        assertThatThrownBy(config::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.datasource.maximum-pool-size");
    }

    @Test
    void rejectsAConnectionTimeoutTheValidationTimeoutCouldNotFitUnder() {
        // The validation timeout floors at 250ms and Hikari rejects one that is not below the
        // connection timeout, so 250 itself is already too low - Hikari would refuse the pool.
        var config = config();
        config.connectionTimeout = 250L;

        assertThatThrownBy(config::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.datasource.connection-timeout");
    }

    @Test
    void acceptsAConnectionTimeoutJustAboveThatFloor() {
        var config = config();
        config.connectionTimeout = 251L;

        assertThatCode(config::validateConfiguration).doesNotThrowAnyException();
    }

    /** A configuration carrying the defaults the @Value annotations declare. */
    private TimeseriesDatabaseConfiguration config() {
        var config = new TimeseriesDatabaseConfiguration();
        config.url = "jdbc:postgresql://localhost:5433/openvsx_timeseries";
        config.username = "openvsx";
        config.password = "openvsx";
        config.maximumPoolSize = 5;
        config.connectionTimeout = 2000L;
        return config;
    }
}
