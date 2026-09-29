/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a PostgreSQL database.
 * <p>
 * The container is a JVM-wide singleton: it is started exactly once (in the static initializer) and
 * shared by every test context, instead of being a context-scoped {@code @ServiceConnection} bean that
 * Spring would start and stop for each distinct application context. With half a dozen distinct
 * {@code @SpringBootTest} context configurations this turned six Postgres startups (and six full Flyway
 * migration runs) into one. Testcontainers' Ryuk sidecar stops the container when the JVM exits, so no
 * explicit shutdown is required.
 * <p>
 * Because all contexts now share a single database, tests must keep cleaning up after themselves (via
 * transactional rollback or an explicit tear-down) and use unique identifiers, exactly as they already
 * had to when sharing a context. That is enough for correctness under JUnit 5 parallel class execution
 * for most of these tests - a handful that scan or lock the whole shared table (consistency checks,
 * analytics aggregation) or rely on precise internal race timing are individually marked
 * {@code @Isolated} on top for that reason.
 * <p>
 * {@code @Isolated} here on the base class is a separate, memory concern rather than a correctness one:
 * a full {@code @SpringBootTest} context can hold onto well over a hundred MB of heap while it is being
 * built, and letting several build at the same time under parallel class execution caused an
 * OutOfMemoryError on CI's smaller runner - not reproducible every run, only once enough of them
 * happened to line up at the same moment. Capping the JUnit 5 thread count
 * ({@code junit-platform.properties}) reduced but did not eliminate it, so every container-backed test
 * runs serially instead, while unrelated unit tests keep the parallel execution win.
 */
@Tag("integration")
@Isolated
public abstract class AbstractPostgresContainerTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse(System.getProperty("ovsx.test.postgres.image", "postgres:16.2"))
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
