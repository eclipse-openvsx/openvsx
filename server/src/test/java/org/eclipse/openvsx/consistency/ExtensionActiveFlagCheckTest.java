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
package org.eclipse.openvsx.consistency;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

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
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the {@code Extension.active} lost-update race: a transaction that never touches
 * {@code active} (e.g. a download-count bump) could load the row before a concurrent delete commits and,
 * on a later commit, blindly rewrite {@code active} back to its stale pre-delete value via Hibernate's
 * default full-row {@code UPDATE}. Covers both the {@code @DynamicUpdate} fix that prevents it going
 * forward, and {@link ExtensionActiveFlagCheck} (run through {@link ConsistencyCheckService}), which
 * repairs rows already left inconsistent by it.
 * <p>
 * {@code @Isolated}: runs {@link ExtensionActiveFlagCheck} through {@link ConsistencyCheckService}, which
 * scans and locks every {@code Extension} row, colliding with unrelated tests concurrently mutating their
 * own.
 */
@SpringBootTest
@Isolated
class ExtensionActiveFlagCheckTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "active-flag-testns";
    private static final String EXTENSION = "active-flag-testext";
    private static final String OWNER_LOGIN = "active-flag-owner";

    @Autowired
    ExtensionService extensionService;

    @Autowired
    ConsistencyCheckService consistencyCheckService;

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    private long extensionId;
    private long ownerId;
    private long ownerTokenId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var owner = new UserData();
            owner.setLoginName(OWNER_LOGIN);
            em.persist(owner);

            var token = new PersonalAccessToken();
            token.setUser(owner);
            token.setValue(OWNER_LOGIN + "_token");
            token.setCreatedTimestamp(LocalDateTime.now());
            token.setActive(true);
            token.setType(PersonalAccessTokenType.LLT);
            em.persist(token);

            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);
            em.flush();

            ownerId = owner.getId();
            ownerTokenId = token.getId();
            extensionId = extension.getId();
        });
    }

    private UserData owner() {
        var owner = new UserData();
        owner.setId(ownerId);
        owner.setLoginName(OWNER_LOGIN);
        return owner;
    }

    /**
     * Reproduces the race directly: a writer transaction loads the extension (sees active=true) and
     * only ever touches an unrelated field (download count), but doesn't commit until after a concurrent
     * delete has deactivated the last active version and committed active=false. Without
     * {@code @DynamicUpdate}, the writer's full-row UPDATE would blindly rewrite active back to true
     * using its stale in-memory snapshot.
     */
    @Test
    void unrelatedConcurrentWrite_doesNotResurrectActiveFlagAfterDelete() throws InterruptedException {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var writerLoaded = new CountDownLatch(1);
        var deleteCommitted = new CountDownLatch(1);
        var writerFailure = new AtomicReference<Throwable>();

        var writer = new Thread(() -> {
            try {
                new TransactionTemplate(txManager).executeWithoutResult(status -> {
                    var extension = em.find(Extension.class, extensionId);
                    assertThat(extension.isActive())
                            .as("the writer must load the pre-delete state to reproduce the race")
                            .isTrue();
                    writerLoaded.countDown();
                    await(deleteCommitted);

                    // Only an unrelated field is touched here - never `active`.
                    extension.setDownloadCount(extension.getDownloadCount() + 1);
                });
            } catch (Throwable t) {
                writerFailure.set(t);
            }
        });
        writer.start();
        writerLoaded.await();

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);
        deleteCommitted.countDown();
        writer.join();

        assertThat(writerFailure.get()).as("the unrelated write must not fail").isNull();
        assertThat(extensionActiveInDb())
                .as(
                        "an unrelated concurrent write must not resurrect `active` after the last active "
                                + "version was deleted")
                .isFalse();
    }

    /**
     * Repairs a row already left inconsistent (as if by the race above, before the {@code @DynamicUpdate}
     * fix existed): {@code active} is forced true directly in the database while no version is active.
     */
    @Test
    void consistencyCheck_fixesStaleActiveFlag() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, false, true);
        forceActiveFlagInDb(true);
        assertThat(extensionActiveInDb()).isTrue();

        var fixed = consistencyCheckService.fixAll(ExtensionActiveFlagCheck.ID);

        assertThat(fixed).isEqualTo(1);
        assertThat(extensionActiveInDb())
                .as("the check must recompute `active` from actual version state")
                .isFalse();
    }

    /**
     * A consistent row (active matches having an active version) must be left untouched.
     */
    @Test
    void consistencyCheck_leavesConsistentExtensionsAlone() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        assertThat(
                repositories.findExtensionsWithInconsistentActiveFlag().stream()
                        .map(Extension::getId)
                        .toList())
                .doesNotContain(extensionId);

        var fixed = consistencyCheckService.fixAll(ExtensionActiveFlagCheck.ID);

        assertThat(fixed).isZero();
        assertThat(extensionActiveInDb()).isTrue();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void persistVersion(String version, String targetPlatform, boolean active, boolean removed) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var extension = em.find(Extension.class, extensionId);
            var token = em.getReference(PersonalAccessToken.class, ownerTokenId);
            var extVersion = new ExtensionVersion();
            extVersion.setVersion(version);
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setExtension(extension);
            extVersion.setPublishedBy(token.getUser());
            extVersion.setActive(active);
            extVersion.setRemoved(removed);
            if (removed) {
                extVersion.setActive(false);
                extVersion.setRemovedTimestamp(LocalDateTime.now());
                extVersion.setRemovedBy(em.getReference(UserData.class, ownerId));
            }
            em.persist(extVersion);
        });
    }

    private void forceActiveFlagInDb(boolean active) {
        new TransactionTemplate(txManager).executeWithoutResult(
                status -> em.createNativeQuery("update extension set active = :active where id = :id")
                        .setParameter("active", active)
                        .setParameter("id", extensionId)
                        .executeUpdate());
    }

    private boolean extensionActiveInDb() {
        return Boolean.TRUE.equals(
                new TransactionTemplate(txManager).execute(
                        status -> (boolean) em
                                .createNativeQuery("select active from extension where id = :id")
                                .setParameter("id", extensionId)
                                .getSingleResult()));
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionVersion ev where ev.extension.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from PersistedLog pl where pl.user.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
            em.createQuery("delete from PersonalAccessToken t where t.user.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from UserData u where u.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
        });
    }
}
