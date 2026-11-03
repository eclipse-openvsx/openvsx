/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExtensionJooqRepository#lockDisplayName} against a real database. A second transaction is opened
 * while the first still holds its lock, with a short lock timeout so that waiting on the lock shows up
 * as a failure instead of a hang.
 */
@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExtensionRepositoryDisplayNameLockTest extends AbstractPostgresContainerTest {

    @Autowired
    ExtensionJooqRepository repo;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;
    TransactionTemplate otherTx;

    /** Rows committed by a test, deleted again in reverse order afterwards. */
    List<Map.Entry<Class<?>, Object>> committed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        otherTx = new TransactionTemplate(transactionManager);
        otherTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> committed.reversed().forEach(row -> {
            var entity = em.find(row.getKey(), row.getValue());
            if (entity != null) {
                em.remove(entity);
            }
        }));
    }

    @Test
    void blocksTheSameNameDifferingOnlyInCasingOrSurroundingWhitespace() {
        tx.executeWithoutResult(status -> {
            repo.lockDisplayName("Demo OK");

            assertThatThrownBy(() -> lockElsewhere("  demo ok "))
                    .as("the name is held until the first transaction ends")
                    .isInstanceOf(PessimisticLockingFailureException.class);
        });
    }

    @Test
    void doesNotBlockAnotherName() {
        tx.executeWithoutResult(status -> {
            repo.lockDisplayName("Demo OK");

            assertThatCode(() -> lockElsewhere("Demo OK Too")).doesNotThrowAnyException();
        });
    }

    @Test
    void releasesTheNameWhenTheTransactionEnds() {
        tx.executeWithoutResult(status -> repo.lockDisplayName("Demo OK"));

        assertThatCode(() -> lockElsewhere("Demo OK")).doesNotThrowAnyException();
    }

    @Test
    void anActivationWaitingOnTheNameSeesTheOneThatHeldIt() throws Exception {
        var displayName = "Lock Race " + UUID.randomUUID();
        var publisher = new UserData();
        publisher.setLoginName("display-name-lock-test-" + UUID.randomUUID());
        var secondNamespace = "lock-race-second-" + UUID.randomUUID();
        // Both pending, as two publications are while their scans run.
        var first = tx.execute(status -> {
            persist(publisher);
            persistPendingVersion(publisher, secondNamespace, displayName);
            return persistPendingVersion(publisher, "lock-race-first-" + UUID.randomUUID(), displayName);
        });

        var executor = Executors.newSingleThreadExecutor();
        var other = new AtomicReference<Future<Extension>>();
        try {
            tx.executeWithoutResult(status -> {
                repo.lockDisplayName(displayName);
                em.find(ExtensionVersion.class, first.getId()).setActive(true);
                em.find(Extension.class, first.getExtension().getId()).setActive(true);
                em.flush();

                other.set(executor.submit(() -> otherTx.execute(otherStatus -> {
                    repo.lockDisplayName(displayName);
                    return repo.findActiveExtensionByDisplayName(displayName, List.of(secondNamespace));
                })));
                assertThat(awaitAdvisoryLockWait(other.get()))
                        .as("the second activation waits while the first holds the name")
                        .isTrue();
            });

            var conflict = other.get().get(10, TimeUnit.SECONDS);
            assertThat(conflict).isNotNull();
            assertThat(conflict.getId()).isEqualTo(first.getExtension().getId());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Whether some backend comes to wait on an advisory lock before {@code other} completes. Observed in
     * the database rather than timed, so a slow start of the other thread can not pass for waiting.
     */
    private boolean awaitAdvisoryLockWait(Future<?> other) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!other.isDone() && System.nanoTime() < deadline) {
            var waiting = ((Number) em.createNativeQuery(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and wait_event = 'advisory'")
                    .getSingleResult()).longValue();
            if (waiting > 0) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private void lockElsewhere(String displayName) {
        otherTx.executeWithoutResult(status -> {
            em.createNativeQuery("set local lock_timeout = '200ms'").executeUpdate();
            repo.lockDisplayName(displayName);
        });
    }

    private ExtensionVersion persistPendingVersion(UserData publisher, String name, String displayName) {
        var namespace = new Namespace();
        namespace.setName(name);
        persist(namespace);

        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setActive(false);
        extension.setDeprecated(false);
        extension.setDownloadable(true);
        extension.setPublishedDate(LocalDateTime.now());
        extension.setLastUpdatedDate(LocalDateTime.now());
        persist(extension);

        var extVersion = new ExtensionVersion();
        extVersion.setPublishedBy(publisher);
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setDisplayName(displayName);
        extVersion.setActive(false);
        extVersion.setTimestamp(LocalDateTime.now());
        persist(extVersion);
        return extVersion;
    }

    private void persist(Object entity) {
        em.persist(entity);
        var id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        committed.add(Map.entry(entity.getClass(), id));
    }
}
