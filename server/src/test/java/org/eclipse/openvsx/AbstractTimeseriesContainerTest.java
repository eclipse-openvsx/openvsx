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

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the time-series database on top of the registry database, i.e.
 * download analytics. Like the registry container, this one is a JVM-wide singleton started once
 * in its static initializer and reaped by Ryuk when the JVM exits.
 * <p>
 * Two containers rather than two databases in one is deliberate: the timescale image installs the
 * extension into {@code template1}, so a second database inside it would still carry timescaledb -
 * exactly the coupling that keeping the two schemas apart is meant to remove. Override the image
 * with {@code -Dovsx.test.timeseries.image=...} if needed.
 */
public abstract class AbstractTimeseriesContainerTest extends AbstractPostgresContainerTest {

    static final PostgreSQLContainer TIMESERIES = new PostgreSQLContainer(
            DockerImageName
                    .parse(System.getProperty("ovsx.test.timeseries.image", "timescale/timescaledb:2.17.2-pg16"))
                    .asCompatibleSubstituteFor("postgres"));

    static {
        TIMESERIES.start();
    }

    @DynamicPropertySource
    static void timeseriesProperties(DynamicPropertyRegistry registry) {
        registry.add("ovsx.analytics.enabled", () -> true);
        registry.add("ovsx.analytics.datasource.url", TIMESERIES::getJdbcUrl);
        registry.add("ovsx.analytics.datasource.username", TIMESERIES::getUsername);
        registry.add("ovsx.analytics.datasource.password", TIMESERIES::getPassword);
        registry.add("ovsx.analytics.datasource.maximum-pool-size", () -> 2);
    }
}
