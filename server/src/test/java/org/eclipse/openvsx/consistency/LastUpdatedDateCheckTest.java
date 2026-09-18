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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the {@code Extension.lastUpdatedDate} lost-update race described in issue #2229:
 * a transaction that never touches {@code lastUpdatedDate} (e.g. a download-count bump) could load the
 * row before a concurrent publish commits and, on a later commit, blindly rewrite {@code lastUpdatedDate}
 * back to its stale pre-publish value via Hibernate's default full-row {@code UPDATE}. Covers both the
 * {@code @DynamicUpdate} fix that prevents it going forward, and {@link LastUpdatedDateCheck} (run
 * through {@link ConsistencyCheckService}), which repairs rows already left inconsistent by it.
 */
@SpringBootTest
class LastUpdatedDateCheckTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "last-updated-testns";
    private static final String EXTENSION = "last-updated-testext";
    private static final String OWNER_LOGIN = "last-updated-owner";

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

    /**
     * Reproduces the race directly: a writer transaction loads the extension (sees the pre-publish
     * last-updated date) and only ever touches an unrelated field (download count), but doesn't commit
     * until after a concurrent publish has stamped a newer last-updated date and committed. Without
     * {@code @DynamicUpdate}, the writer's full-row UPDATE would blindly rewrite it back using its stale
     * in-memory snapshot.
     */
    @Test
    void unrelatedConcurrentWrite_doesNotRevertLastUpdatedDateAfterPublish() throws InterruptedException {
        var initialTimestamp = LocalDateTime.now().minusDays(10).truncatedTo(ChronoUnit.MICROS);
        persistVersion("1.0.0", initialTimestamp, true, false);
        forceLastUpdatedDateInDb(initialTimestamp);

        var writerLoaded = new CountDownLatch(1);
        var publishCommitted = new CountDownLatch(1);
        var writerFailure = new AtomicReference<Throwable>();

        var writer = new Thread(() -> {
            try {
                new TransactionTemplate(txManager).executeWithoutResult(status -> {
                    var extension = em.find(Extension.class, extensionId);
                    assertThat(extension.getLastUpdatedDate())
                            .as("the writer must load the pre-publish state to reproduce the race")
                            .isEqualTo(initialTimestamp);
                    writerLoaded.countDown();
                    await(publishCommitted);

                    // Only an unrelated field is touched here - never lastUpdatedDate.
                    extension.setDownloadCount(extension.getDownloadCount() + 1);
                });
            } catch (Throwable t) {
                writerFailure.set(t);
            }
        });
        writer.start();
        writerLoaded.await();

        var publishedTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        persistVersion("1.0.1", publishedTimestamp, true, false);
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var extension = em.find(Extension.class, extensionId);
            extension.setLastUpdatedDate(publishedTimestamp);
        });
        publishCommitted.countDown();
        writer.join();

        assertThat(writerFailure.get()).as("the unrelated write must not fail").isNull();
        assertThat(lastUpdatedDateInDb())
                .as("an unrelated concurrent write must not revert last_updated_date after a publish")
                .isEqualTo(publishedTimestamp);
    }

    /**
     * Repairs a row already left inconsistent (as if by the race above, before the {@code @DynamicUpdate}
     * fix existed): last_updated_date is forced to an old value directly in the database while a newer
     * active version exists.
     */
    @Test
    void consistencyCheck_fixesStaleLastUpdatedDate() {
        var oldTimestamp = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.MICROS);
        var latestTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        persistVersion("1.0.0", oldTimestamp, true, false);
        persistVersion("1.0.1", latestTimestamp, true, false);
        forceLastUpdatedDateInDb(oldTimestamp);

        assertThat(
                repositories.findExtensionsWithStaleLastUpdatedDate().stream()
                        .map(Extension::getId)
                        .toList())
                .contains(extensionId);

        var fixed = consistencyCheckService.fixAll(LastUpdatedDateCheck.ID);

        assertThat(fixed).isEqualTo(1);
        assertThat(lastUpdatedDateInDb())
                .as("the check must recompute last_updated_date from the latest active version's timestamp")
                .isEqualTo(latestTimestamp);
    }

    /**
     * A consistent row (last_updated_date already at or after its latest active version) must be left
     * untouched.
     */
    @Test
    void consistencyCheck_leavesConsistentExtensionsAlone() {
        var timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        persistVersion("1.0.0", timestamp, true, false);
        forceLastUpdatedDateInDb(timestamp);

        assertThat(
                repositories.findExtensionsWithStaleLastUpdatedDate().stream()
                        .map(Extension::getId)
                        .toList())
                .doesNotContain(extensionId);

        var fixed = consistencyCheckService.fixAll(LastUpdatedDateCheck.ID);

        assertThat(fixed).isZero();
        assertThat(lastUpdatedDateInDb()).isEqualTo(timestamp);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void persistVersion(String version, LocalDateTime timestamp, boolean active, boolean removed) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var extension = em.find(Extension.class, extensionId);
            var token = em.getReference(PersonalAccessToken.class, ownerTokenId);
            var extVersion = new ExtensionVersion();
            extVersion.setVersion(version);
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setExtension(extension);
            extVersion.setPublishedBy(token.getUser());
            extVersion.setTimestamp(timestamp);
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

    private void forceLastUpdatedDateInDb(LocalDateTime lastUpdatedDate) {
        new TransactionTemplate(txManager).executeWithoutResult(
                status -> em
                        .createNativeQuery("update extension set last_updated_date = :lastUpdatedDate where id = :id")
                        .setParameter("lastUpdatedDate", lastUpdatedDate)
                        .setParameter("id", extensionId)
                        .executeUpdate());
    }

    private LocalDateTime lastUpdatedDateInDb() {
        return new TransactionTemplate(txManager).execute(
                status -> ((java.sql.Timestamp) em
                        .createNativeQuery("select last_updated_date from extension where id = :id")
                        .setParameter("id", extensionId)
                        .getSingleResult())
                        .toLocalDateTime());
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
