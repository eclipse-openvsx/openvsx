/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.consistency;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the generic orchestration {@link ConsistencyCheckService} provides on top of whatever
 * {@link ConsistencyCheck} beans are registered - using the real {@link ExtensionActiveFlagCheck} as the
 * one currently wired up, rather than a fake, so this also doubles as an end-to-end check that a
 * registered check is actually picked up with no further wiring.
 * <p>
 * {@code @Isolated}: {@code runAllChecks} scans and locks every {@code Extension} row, colliding with
 * unrelated tests concurrently mutating their own.
 */
@SpringBootTest
@Isolated
class ConsistencyCheckServiceTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "consistency-service-testns";
    private static final String EXTENSION = "consistency-service-testext";

    @Autowired
    ConsistencyCheckService service;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(namespace);
            extension.setActive(true); // inconsistent on purpose: no version exists at all
            em.persist(extension);
        });
    }

    @Test
    void listSummaries_includesEveryRegisteredCheckWithLiveCount() {
        var summaries = service.listSummaries();

        var extensionActiveFlag = summaries.stream()
                .filter(s -> s.id().equals(ExtensionActiveFlagCheck.ID))
                .findFirst()
                .orElseThrow();
        assertThat(extensionActiveFlag.currentFindingsCount())
                .as("the extension created in setUp is currently inconsistent")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void findings_throwsNotFoundForAnUnregisteredCheckId() {
        assertThatThrownBy(() -> service.findings("does-not-exist")).isInstanceOf(NotFoundException.class);
    }

    /**
     * ExtensionActiveFlagCheck auto-fixes on schedule by default (a pure recomputation, always safe
     * unattended), so a run must both leave the database actually fixed and record what it did as a
     * normal admin log entry attributed to the dedicated system user - not a bespoke history table.
     */
    @Test
    void runAllChecks_autoFixesAndLogsUnderTheSystemUser() {
        service.runAllChecks();

        var summary = service.listSummaries().stream()
                .filter(s -> s.id().equals(ExtensionActiveFlagCheck.ID))
                .findFirst()
                .orElseThrow();
        assertThat(summary.currentFindingsCount())
                .as("the finding must actually be fixed by the run, not just reported")
                .isZero();

        assertThat(logMessagesForSystemUser())
                .as("the fix must be recorded as an admin log entry attributed to the system user")
                .anyMatch(message -> message.contains(ExtensionActiveFlagCheck.ID));
    }

    private List<String> logMessagesForSystemUser() {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select p.message from PersistedLog p where p.user.loginName = :loginName",
                        String.class)
                        .setParameter("loginName", "ConsistencyCheckUser")
                        .getResultList());
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from PersistedLog p where p.user.loginName = :loginName")
                    .setParameter("loginName", "ConsistencyCheckUser").executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
        });
    }
}
