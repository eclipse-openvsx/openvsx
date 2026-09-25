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

import java.time.LocalDateTime;

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
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExtensionVersionChangeRepository#findFirstByExtensionVersionAndNamespaceOrderByChangedAtDescIdDesc}
 * exists because a version's history can span more than one namespace once a rename is reported on it -
 * {@link RepositoryService#wasReportedAsAvailable} has to resolve the latest state <em>for the namespace
 * the version currently lives in</em>, not the single most recent entry regardless of namespace, or a
 * newer entry appended for an abandoned namespace (e.g. by the #2244 backfill migration, or a later
 * rename) would answer for the wrong tuple entirely.
 */
@SpringBootTest(
    classes = ExtensionVersionChangeRepositoryTest.ExtensionVersionChangeRepositoryTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Transactional
class ExtensionVersionChangeRepositoryTest extends AbstractPostgresContainerTest {

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
        basePackageClasses = ExtensionVersionChangeRepository.class,
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ExtensionVersionChangeRepository.class)
    )
    static class ExtensionVersionChangeRepositoryTestConfig {
    }

    @Autowired
    ExtensionVersionChangeRepository repo;

    @Autowired
    EntityManager em;

    ExtensionVersion extVersion;

    @BeforeEach
    void persistRenamedVersion() {
        var currentNamespace = persistNamespace("current-namespace");
        var extension = new Extension();
        extension.setName("ext");
        extension.setNamespace(currentNamespace);
        em.persist(extension);

        extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        em.persist(extVersion);
    }

    @Test
    void resolvesTheLatestStateWithinTheGivenNamespaceOnly() {
        // the version's original, now-abandoned namespace
        change("old-namespace", ExtensionVersionState.ACTIVE, LocalDateTime.parse("2026-01-01T00:00"));
        // a later, correctly-recorded transition under the namespace the version lives in today
        change("current-namespace", ExtensionVersionState.INACTIVE, LocalDateTime.parse("2026-01-02T00:00"));
        // a namespace-rename backfill closing the abandoned tuple, stamped with its own (later) run time -
        // newer than every entry above, including the one for the namespace actually in use today
        change("old-namespace", ExtensionVersionState.REMOVED, LocalDateTime.parse("2026-06-01T00:00"));

        // the version's single most recent entry overall is the old namespace's backfilled tombstone -
        // exactly the entry that must NOT be mistaken for the current namespace's own state
        assertThat(repo.findFirstByExtensionVersionOrderByChangedAtDescIdDesc(extVersion))
                .get()
                .extracting(ExtensionVersionChange::getNamespace, ExtensionVersionChange::getState)
                .containsExactly("old-namespace", ExtensionVersionState.REMOVED);

        // scoped to the current namespace, the version is correctly still seen as INACTIVE, not REMOVED
        assertThat(
                repo.findFirstByExtensionVersionAndNamespaceOrderByChangedAtDescIdDesc(
                        extVersion,
                        "current-namespace"))
                .get()
                .extracting(ExtensionVersionChange::getState)
                .isEqualTo(ExtensionVersionState.INACTIVE);

        // and the old namespace's own history correctly ends at its tombstone
        assertThat(
                repo.findFirstByExtensionVersionAndNamespaceOrderByChangedAtDescIdDesc(extVersion, "old-namespace"))
                .get()
                .extracting(ExtensionVersionChange::getState)
                .isEqualTo(ExtensionVersionState.REMOVED);
    }

    @Test
    void isEmptyForANamespaceTheVersionWasNeverReportedUnder() {
        change("old-namespace", ExtensionVersionState.ACTIVE, LocalDateTime.parse("2026-01-01T00:00"));

        assertThat(
                repo.findFirstByExtensionVersionAndNamespaceOrderByChangedAtDescIdDesc(
                        extVersion,
                        "current-namespace"))
                .isEmpty();
    }

    private void change(String namespace, ExtensionVersionState state, LocalDateTime changedAt) {
        var change = new ExtensionVersionChange();
        change.setExtensionVersion(extVersion);
        change.setNamespace(namespace);
        change.setExtension(extVersion.getExtension().getName());
        change.setVersion(extVersion.getVersion());
        change.setState(state);
        change.setChangedAt(changedAt);
        em.persist(change);
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        em.persist(namespace);
        return namespace;
    }
}
