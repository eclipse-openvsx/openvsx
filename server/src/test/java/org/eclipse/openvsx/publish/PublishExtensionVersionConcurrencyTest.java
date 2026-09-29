/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.publish;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Concurrency tests for the very first publication of an extension.
 * <p>
 * Publishing takes a write lock on the extension row before adding a version, but that lock does not
 * exist yet while the extension itself is being created: {@code SELECT … FOR UPDATE} on a row that
 * does not exist locks nothing. Two publications racing for the first version of the same extension
 * (typically the platform-specific packages of one release, published in parallel) therefore both saw
 * that the extension was missing and both inserted it, so the loser failed with a duplicate key error
 * on the {@code unique_extension} index.
 * <p>
 * The races are deliberately triggered by intercepting the extension lookup (using a spy) and letting
 * the competing writer commit in exactly the window that used to break.
 * <p>
 * {@code @Isolated}: the deliberately-triggered race depends on precise internal timing between two
 * threads, which unrelated concurrently-running tests competing for CPU can throw off.
 */
@SpringBootTest
@Isolated
class PublishExtensionVersionConcurrencyTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "race-publishns";
    private static final String EXTENSION = "race-publishext";
    private static final String PUBLISHER_LOGIN = "race-publisher";
    private static final String VERSION = "1.0.0";

    // Time given to an operation that is expected to complete.
    private static final long COMPLETION_TIMEOUT_SECONDS = 30;

    // Time an operation blocked on the namespace lock gets to prove that it is not blocked.
    private static final long BLOCKED_TIMEOUT_SECONDS = 2;

    @Autowired
    PublishExtensionVersionHandler publishHandler;

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

    private long namespaceId;
    private long userId;
    private long tokenId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var user = new UserData();
            user.setLoginName(PUBLISHER_LOGIN);
            em.persist(user);

            var token = new PersonalAccessToken();
            token.setUser(user);
            token.setValue(PUBLISHER_LOGIN + "_token");
            token.setCreatedTimestamp(LocalDateTime.now());
            token.setActive(true);
            token.setType(PersonalAccessTokenType.LLT);
            em.persist(token);

            // The namespace exists, the extension does not: this is a first-time publication.
            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var membership = new NamespaceMembership();
            membership.setUser(user);
            membership.setNamespace(namespace);
            membership.setRole(NamespaceMembership.ROLE_OWNER);
            em.persist(membership);
            em.flush();

            namespaceId = namespace.getId();
            userId = user.getId();
            tokenId = token.getId();
        });
    }

    /**
     * The reported race: two platform-specific packages of the same new extension are published at the
     * same time. Both must succeed, and both versions must end up on a single extension record.
     */
    @Test
    void createExtensionVersion_addsToTheExtensionCreatedByAConcurrentPublication() throws Exception {
        var mainThread = Thread.currentThread();
        var raceTriggered = new AtomicBoolean();
        var otherPublishSucceeded = new AtomicBoolean();
        var otherPublishFinished = new CountDownLatch(1);
        var otherPublishFailure = new AtomicReference<Throwable>();

        // Let the competing publication create and commit the extension while this one has already
        // seen that it does not exist yet.
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (Thread.currentThread() == mainThread && result == null && raceTriggered.compareAndSet(false, true)) {
                var other = new Thread(() -> {
                    try {
                        publish(TargetPlatform.NAME_LINUX_X64);
                        otherPublishSucceeded.set(true);
                    } catch (Throwable t) {
                        otherPublishFailure.set(t);
                    } finally {
                        otherPublishFinished.countDown();
                    }
                }, "concurrent-publisher");
                other.start();
                otherPublishFinished.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return result;
        }).when(repositories).findExtensionForUpdate(EXTENSION, NAMESPACE);

        var extVersion = publish(TargetPlatform.NAME_WIN32_X64);

        assertThat(raceTriggered).as("the race must have been triggered, otherwise nothing was tested").isTrue();
        assertThat(otherPublishFinished.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(otherPublishFailure.get())
                .as("the publication that created the extension must not fail")
                .isNull();
        assertThat(otherPublishSucceeded).isTrue();

        // The publication that lost the race must have serialized on the namespace row and picked up
        // the extension created by the winner, instead of inserting a second one. Both publications
        // take the lock once — the winner to create the extension, the loser to discover it — so a
        // third call would mean the loser had to fall back to a retry.
        verify(repositories, times(2)).lockNamespace(any());
        assertThat(extensionIds())
                .as("concurrent first-time publications must not create the extension twice")
                .hasSize(1);
        assertThat(extVersion.getExtension().getId()).isEqualTo(extensionIds().getFirst());
        assertThat(targetPlatformsOfPublishedVersions())
                .containsExactlyInAnyOrder(TargetPlatform.NAME_WIN32_X64, TargetPlatform.NAME_LINUX_X64);
    }

    /**
     * Lock contract: while the namespace row is locked, a publication that has to create the extension
     * must wait for that lock rather than racing ahead into the unique index.
     */
    @Test
    void createExtensionVersion_waitsForTheNamespaceLockBeforeCreatingTheExtension() throws Exception {
        var lockHeld = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);

        // Uses a self-contained SELECT ... FOR NO KEY UPDATE so this test does not depend on the
        // production lock method (and therefore also fails on a fix that locks nothing).
        var lockHolder = new Thread(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("select id from namespace where id = ?1 for no key update")
                    .setParameter(1, namespaceId)
                    .getSingleResult();
            lockHeld.countDown();
            try {
                releaseLock.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "namespace-lock-holder");
        lockHolder.start();
        assertThat(lockHeld.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var publishSucceeded = new AtomicBoolean();
        var publishFinished = new CountDownLatch(1);
        var publisher = new Thread(() -> {
            try {
                publish(TargetPlatform.NAME_WIN32_X64);
                publishSucceeded.set(true);
            } finally {
                publishFinished.countDown();
            }
        }, "publisher");

        try {
            publisher.start();
            assertThat(publishFinished.await(BLOCKED_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("creating the extension must block while the namespace row is locked")
                    .isFalse();
        } finally {
            releaseLock.countDown();
            lockHolder.join(TimeUnit.SECONDS.toMillis(COMPLETION_TIMEOUT_SECONDS));
        }

        assertThat(publishFinished.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the publication must proceed once the namespace lock is released")
                .isTrue();
        assertThat(publishSucceeded).isTrue();
        assertThat(extensionIds()).hasSize(1);
    }

    /**
     * Retry backstop: a writer that creates the extension without taking the namespace lock (e.g. a
     * namespace change moving an extension in) can still win the race. The publication then runs into
     * the unique index and must recover by retrying, not by failing the publisher's request.
     */
    @Test
    void createExtensionVersion_retriesWhenAnotherWriterCreatedTheExtension() {
        var mainThread = Thread.currentThread();
        var lookups = new AtomicInteger();
        var lookupAfterLock = new AtomicReference<Object>();
        var conflictingExtensionId = new AtomicReference<Long>();

        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            // The second lookup is the one after the namespace lock was taken; a writer committing
            // right after it leaves this publication with no way to see the extension.
            if (Thread.currentThread() == mainThread && lookups.incrementAndGet() == 2) {
                lookupAfterLock.set(result);
                conflictingExtensionId.set(createExtensionInSeparateTransaction());
            }
            return result;
        }).when(repositories).findExtensionForUpdate(EXTENSION, NAMESPACE);

        var extVersion = publish(TargetPlatform.NAME_WIN32_X64);

        assertThat(lookupAfterLock.get())
                .as("the extension must still have been absent when the conflict was injected")
                .isNull();
        assertThat(lookups.get())
                .as("the publication must have been retried, adding a third lookup")
                .isEqualTo(3);
        assertThat(extensionIds())
                .as("the retry must reuse the extension created by the other writer")
                .containsExactly(conflictingExtensionId.get());
        assertThat(extVersion.getExtension().getId()).isEqualTo(conflictingExtensionId.get());
        assertThat(targetPlatformsOfPublishedVersions()).containsExactly(TargetPlatform.NAME_WIN32_X64);
    }

    private ExtensionVersion publish(String targetPlatform) {
        try (var processor = mockProcessor(targetPlatform)) {
            return publishHandler
                    .createExtensionVersion(
                            processor,
                            new AccessTokenAuthentication(
                                    publishToken().getUser(),
                                    publishToken().getType(),
                                    publishToken().getId(),
                                    null),
                            LocalDateTime.now(),
                            false);
        }
    }

    private ExtensionProcessor mockProcessor(String targetPlatform) {
        var processor = mock(ExtensionProcessor.class);
        when(processor.getNamespace()).thenReturn(NAMESPACE);
        when(processor.getExtensionName()).thenReturn(EXTENSION);
        when(processor.getVersion()).thenReturn(VERSION);
        when(processor.getTargetPlatform()).thenReturn(targetPlatform);
        when(processor.getExtensionDependencies()).thenReturn(List.of());
        when(processor.getBundledExtensions()).thenReturn(List.of());
        when(processor.getPackageMetadata())
                .thenReturn(new ExtensionProcessor.PackageMetadata(NAMESPACE, EXTENSION, VERSION, "Race Test"));
        // Like the real processor, hand out a fresh instance per call: a retried attempt must not
        // reuse the entity of the attempt that was rolled back.
        when(processor.getMetadata(anyInt(), anyInt())).thenAnswer(invocation -> {
            var extVersion = new ExtensionVersion();
            extVersion.setVersion(VERSION);
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setDisplayName("Race Test");
            return extVersion;
        });
        return processor;
    }

    /**
     * The publishing token as the publish endpoint passes it in: detached, with its user attached.
     */
    private PersonalAccessToken publishToken() {
        var user = new UserData();
        user.setId(userId);
        user.setLoginName(PUBLISHER_LOGIN);
        var token = new PersonalAccessToken();
        token.setId(tokenId);
        token.setUser(user);
        token.setType(PersonalAccessTokenType.LLT);
        return token;
    }

    /**
     * Creates the extension in its own transaction, without taking the namespace lock, and returns its id.
     */
    private long createExtensionInSeparateTransaction() {
        var template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(em.getReference(Namespace.class, namespaceId));
            extension.setActive(false);
            extension.setDeprecated(false);
            extension.setDownloadable(true);
            extension.setPublishedDate(LocalDateTime.now());
            extension.setLastUpdatedDate(LocalDateTime.now());
            em.persist(extension);
            em.flush();
            return extension.getId();
        });
    }

    private List<Long> extensionIds() {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select e.id from Extension e where e.name = :name and e.namespace.name = :namespace",
                        Long.class)
                        .setParameter("name", EXTENSION)
                        .setParameter("namespace", NAMESPACE)
                        .getResultList());
    }

    private List<String> targetPlatformsOfPublishedVersions() {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select ev.targetPlatform from ExtensionVersion ev "
                                + "where ev.extension.name = :name and ev.extension.namespace.name = :namespace",
                        String.class)
                        .setParameter("name", EXTENSION)
                        .setParameter("namespace", NAMESPACE)
                        .getResultList());
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionVersion ev where ev.extension.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from NamespaceMembership nm where nm.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from PersonalAccessToken t where t.user.loginName = :login")
                    .setParameter("login", PUBLISHER_LOGIN).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from UserData u where u.loginName = :login")
                    .setParameter("login", PUBLISHER_LOGIN).executeUpdate();
        });
    }
}
