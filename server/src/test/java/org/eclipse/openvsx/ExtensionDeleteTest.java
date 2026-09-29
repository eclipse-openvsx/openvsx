/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
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
package org.eclipse.openvsx;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Concurrency tests for delete-all operations in {@link ExtensionService} and
 * {@link AdminService}.
 * <p>
 * Both services expose a delete-all path that first checks how many versions exist
 * and then deletes them. Without an extension-row lock, a concurrent publish can slip
 * in between the check and the actual deletion and have its newly committed version
 * silently removed as a side effect.
 * <p>
 * The race condition is deliberately triggered by intercepting the boundary call that
 * separates the check from the act (using a spy), pausing the delete operation just
 * long enough for the concurrent publish to commit.
 */
@SpringBootTest
class ExtensionDeleteTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "race-testns";
    private static final String EXTENSION = "race-testext";
    private static final String OWNER_LOGIN = "race-owner";
    private static final String OTHER_LOGIN = "race-other";

    // Max wait in the race window for the publish to commit: fast without the lock, times out with it.
    private static final long RACE_WINDOW_TIMEOUT_SECONDS = 3;

    // How long the simulated in-progress publish holds the extension lock before giving up.
    private static final long LOCK_HOLD_TIMEOUT_SECONDS = 5;

    @Autowired
    ExtensionService extensionService;

    @MockitoSpyBean
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
    private long otherTokenId;

    private final AtomicBoolean raceTriggered = new AtomicBoolean(false);
    private final AtomicBoolean publisherSucceeded = new AtomicBoolean(false);
    private final CountDownLatch publisherFinished = new CountDownLatch(1);
    private volatile Thread publisherThread;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var owner = persistUser(OWNER_LOGIN);
            var ownerToken = persistToken(owner);
            var other = persistUser(OTHER_LOGIN);
            var otherToken = persistToken(other);

            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);

            // The owner is the only publisher of the extension at check time.
            em.persist(newVersion("1.0.0", extension, owner));
            em.flush();

            ownerId = owner.getId();
            otherTokenId = otherToken.getId();
            extensionId = extension.getId();
        });
    }

    @Test
    void userDeleteExtension_doesNotDeleteAnotherPublishersConcurrentlyAddedVersion() throws Exception {
        // Force a concurrent publish into the window between the check and the actual deletion.
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (raceTriggered.compareAndSet(false, true)) {
                startConcurrentPublish();
                publisherFinished.await(RACE_WINDOW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return result;
        }).when(repositories).isDeleteAllActiveVersions(any(), any(), any(), any());

        var owner = new UserData();
        owner.setId(ownerId);
        owner.setLoginName(OWNER_LOGIN);

        // The owner asks to delete the only version they published.
        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner, false, NAMESPACE, EXTENSION, targets);

        // Let the (possibly blocked) publisher run to completion now that the delete operation committed.
        if (publisherThread != null) {
            publisherThread.join(TimeUnit.SECONDS.toMillis(10));
        }

        var otherVersionExists = versionExists("2.0.0");
        assertThat(publisherSucceeded.get() && !otherVersionExists)
                .as(
                        "a version successfully published by another user must never be silently "
                                + "deleted by a concurrent delete-all on the same extension")
                .isFalse();
        assertThat(otherVersionExists && !extensionExists())
                .as("a committed version must never be left orphaned by a removed extension")
                .isFalse();
    }

    /**
     * When another publisher already has a version, deleting your own version must not be treated
     * as a delete-all: the extension record survives and the other publisher's version stays intact
     * (not removed, not orphaned).
     */
    @Test
    void deleteExtension_keepsExtensionAndOtherUsersVersionWhenDeletingOwnVersion() {
        // A different publisher already has a version of the same extension.
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var extension = em.find(Extension.class, extensionId);
            var token = em.getReference(PersonalAccessToken.class, otherTokenId);
            em.persist(newVersion("2.0.0", extension, token.getUser()));
        });

        var owner = new UserData();
        owner.setId(ownerId);
        owner.setLoginName(OWNER_LOGIN);

        // The owner deletes only their own version; this is not a delete-all.
        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner, false, NAMESPACE, EXTENSION, targets);

        assertThat(extensionExists())
                .as("the extension must survive while another publisher's version remains")
                .isTrue();
        assertThat(versionExists("2.0.0"))
                .as("another publisher's version must not be removed or orphaned")
                .isTrue();
        assertThat(versionExists("1.0.0"))
                .as("the owner's deleted version row must be kept as an immutable tombstone")
                .isTrue();
        assertThat(versionRemoved("1.0.0"))
                .as("the owner's deleted version must be marked removed and inactive")
                .isTrue();
    }

    /**
     * Pessimistic-lock contract: when a publish is in progress (holding the extension-row write
     * lock), a concurrent delete-all must fail fast and ask the user to retry, rather than blocking
     * or removing the extension out from under the publish. On retry the new version exists, so it
     * is no longer a delete-all.
     */
    @Test
    void deleteExtension_failsFastWhenAPublishHoldsTheLock() throws Exception {
        var lockHeld = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);

        // Simulate a publish in progress by holding the same extension-row write lock it takes.
        // Uses a self-contained SELECT ... FOR UPDATE so this test does not depend on the
        // production lock method (and therefore compiles on its own, before the fix).
        var lockHolder = new Thread(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("select id from extension where id = ?1 for update")
                    .setParameter(1, extensionId)
                    .getSingleResult();
            lockHeld.countDown();
            try {
                releaseLock.await(LOCK_HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "publish-lock-holder");
        lockHolder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        var owner = new UserData();
        owner.setId(ownerId);
        owner.setLoginName(OWNER_LOGIN);
        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");

        try {
            assertThatThrownBy(() -> extensionService.deleteExtension(owner, false, NAMESPACE, EXTENSION, targets))
                    .as("the delete must fail fast (not block) while a publish holds the lock")
                    .isInstanceOf(ErrorResultException.class);
        } finally {
            releaseLock.countDown();
            lockHolder.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertThat(extensionExists())
                .as("a delete that lost the race to a publish must not have removed the extension")
                .isTrue();
        assertThat(versionExists("1.0.0"))
                .as("a delete that lost the race to a publish must not have removed the version")
                .isTrue();
    }

    private void startConcurrentPublish() {
        publisherThread = new Thread(() -> {
            try {
                new TransactionTemplate(txManager).executeWithoutResult(status -> {
                    var extension = em.find(Extension.class, extensionId);
                    var token = em.getReference(PersonalAccessToken.class, otherTokenId);
                    em.persist(newVersion("2.0.0", extension, token.getUser()));
                    // Publishing updates the extension row; this write contends with the delete's lock.
                    extension.setLastUpdatedDate(LocalDateTime.now());
                    em.flush();
                });
                publisherSucceeded.set(true);
            } catch (Exception e) {
                // Expected with the lock: the extension is gone by the time the insert unblocks.
                publisherSucceeded.set(false);
            } finally {
                publisherFinished.countDown();
            }
        }, "concurrent-publisher");
        publisherThread.start();
    }

    private boolean versionExists(String version) {
        return Boolean.TRUE.equals(
                new TransactionTemplate(txManager).execute(
                        status -> !em.createQuery(
                                "select ev.id from ExtensionVersion ev "
                                        + "where ev.version = :version "
                                        + "and ev.extension.namespace.name = :namespace")
                                .setParameter("version", version)
                                .setParameter("namespace", NAMESPACE)
                                .getResultList()
                                .isEmpty()));
    }

    private boolean versionRemoved(String version) {
        return Boolean.TRUE.equals(
                new TransactionTemplate(txManager).execute(
                        status -> !em.createQuery(
                                "select ev.id from ExtensionVersion ev "
                                        + "where ev.version = :version "
                                        + "and ev.extension.namespace.name = :namespace "
                                        + "and ev.removed = true and ev.active = false")
                                .setParameter("version", version)
                                .setParameter("namespace", NAMESPACE)
                                .getResultList()
                                .isEmpty()));
    }

    private boolean extensionExists() {
        return Boolean.TRUE.equals(
                new TransactionTemplate(txManager).execute(
                        status -> !em.createQuery(
                                "select e.id from Extension e "
                                        + "where e.name = :name and e.namespace.name = :namespace")
                                .setParameter("name", EXTENSION)
                                .setParameter("namespace", NAMESPACE)
                                .getResultList()
                                .isEmpty()));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (publisherThread != null) {
            publisherThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionVersion ev where ev.extension.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from PersistedLog pl where pl.user.loginName in :logins")
                    .setParameter("logins", List.of(OWNER_LOGIN, OTHER_LOGIN)).executeUpdate();
            em.createQuery("delete from PersonalAccessToken t where t.user.loginName in :logins")
                    .setParameter("logins", List.of(OWNER_LOGIN, OTHER_LOGIN)).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from UserData u where u.loginName in :logins")
                    .setParameter("logins", List.of(OWNER_LOGIN, OTHER_LOGIN)).executeUpdate();
        });
    }

    private UserData persistUser(String loginName) {
        var user = new UserData();
        user.setLoginName(loginName);
        em.persist(user);
        return user;
    }

    private PersonalAccessToken persistToken(UserData user) {
        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setValue(user.getLoginName() + "_token");
        token.setCreatedTimestamp(LocalDateTime.now());
        token.setActive(true);
        token.setType(PersonalAccessTokenType.LLT);
        em.persist(token);
        return token;
    }

    private ExtensionVersion newVersion(String version, Extension extension, UserData user) {
        var extVersion = new ExtensionVersion();
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        extVersion.setPublishedBy(user);
        extVersion.setActive(true);
        return extVersion;
    }
}
