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

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * A {@code @SpringBootTest(classes = ...)} root for tests of the jOOQ-based repository classes
 * ({@link ExtensionVersionJooqRepository}, {@link ExtensionJooqRepository}) - plain {@code @Component}s
 * that talk to the database directly through jOOQ, not Spring Data JPA repository interfaces.
 * <p>
 * Booting the real {@code RegistryApplication} for these tests (as a bare {@code @SpringBootTest} would)
 * pulls in every auto-configuration the application has: all ~30 Spring Data JPA repository interfaces
 * (each one's derived/{@code @Query} methods individually parsed and validated against the Hibernate
 * metamodel - the dominant cost, on the order of a minute, of a full application context in this
 * project), JobRunr's SQL storage provider, the web/MVC layer, security, actuator, and more - none of
 * which either repository class needs. This imports only what {@code EntityManager}-based test setup
 * and the jOOQ repositories themselves require: a DataSource, Flyway (to create the schema), Hibernate/JPA
 * (for the EntityManager and transaction management the tests use to set up fixtures), and jOOQ's
 * {@code DSLContext}.
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(
    {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JooqAutoConfiguration.class
    }
)
@EntityScan("org.eclipse.openvsx.entities")
@Import({ ExtensionVersionJooqRepository.class, ExtensionJooqRepository.class })
public class JooqOnlyTestConfig {
}
