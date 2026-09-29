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

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Namespace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NamespaceRepository} is the only Spring Data JPA repository interface this test needs, but a
 * bare {@code @SpringBootTest} would still boot the entire {@code RegistryApplication} regardless -
 * every one of the app's ~30 JPA repository interfaces (each one's derived query methods individually
 * parsed and validated against the Hibernate metamodel - the dominant cost of a full context in this
 * project), JobRunr, the web layer, and more. {@link NamespaceRepositoryTestConfig}'s
 * {@code includeFilters} restricts {@code @EnableJpaRepositories} to just this one interface instead of
 * every repository interface {@code org.eclipse.openvsx.repositories} (its base package) contains.
 */
@SpringBootTest(
    classes = NamespaceRepositoryTest.NamespaceRepositoryTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Transactional
class NamespaceRepositoryTest extends AbstractPostgresContainerTest {

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(
        {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class
        }
    )
    @EntityScan("org.eclipse.openvsx.entities")
    @EnableJpaRepositories(
        basePackageClasses = NamespaceRepository.class,
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = NamespaceRepository.class)
    )
    static class NamespaceRepositoryTestConfig {
    }

    @Autowired
    NamespaceRepository repo;

    @Autowired
    EntityManager em;

    Namespace github;
    Namespace other;

    @BeforeEach
    void persistNamespaces() {
        github = persistNamespace("github", "The GitHub Org");
        other = persistNamespace("other", "Other Org");
    }

    @Test
    void findMultipleConflictingNamespaceMatches() {
        persistNamespace("github2", "github");
        var conflicts = repo.findConflictingNamespaces("GITHUB", other);

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.getFirst().getName()).isEqualTo("github");
    }

    @Test
    void findConflictingNamespaceMatchesNameIgnoringCase() {
        var conflicts = repo.findConflictingNamespaces("GITHUB", other);

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.getFirst().getName()).isEqualTo("github");
    }

    @Test
    void findConflictingNamespaceMatchesDisplayNameIgnoringCase() {
        var conflicts = repo.findConflictingNamespaces("the github org", other);

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.getFirst().getName()).isEqualTo("github");
    }

    @Test
    void findConflictingNamespaceExcludesOwnNamespaceIgnoringCase() {
        var conflicts = repo.findConflictingNamespaces("The GitHub Org", github);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void findConflictingNamespaceStillFindsOtherNamespaces() {
        var dup = persistNamespace("dup", "The GitHub Org");

        var conflicts = repo.findConflictingNamespaces("the github org", github);

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.getFirst().getName()).isEqualTo(dup.getName());
    }

    @Test
    void findConflictingNamespaceEmptyWhenNothingMatches() {
        var conflicts = repo.findConflictingNamespaces("Brand New", github);

        assertThat(conflicts).isEmpty();
    }

    private Namespace persistNamespace(String name, String displayName) {
        var namespace = new Namespace();
        namespace.setName(name);
        namespace.setDisplayName(displayName);
        em.persist(namespace);
        return namespace;
    }
}
