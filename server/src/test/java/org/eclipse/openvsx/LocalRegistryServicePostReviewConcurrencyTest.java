/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.ReviewJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.security.IdPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * GHSA-69xx-h5fw-vcpq: postReview()'s hasActiveReview() check and the review insert are a check-then-act
 * pair with no serialization point between them, so two concurrent requests from the same user can both
 * pass the check under READ COMMITTED and both insert an active review. The race is deliberately
 * triggered by intercepting the duplicate-review check (using a spy) and letting the competing writer
 * commit in exactly the window that used to break, exactly as PublishExtensionVersionConcurrencyTest does
 * for the analogous extension-creation race.
 */
@SpringBootTest
class LocalRegistryServicePostReviewConcurrencyTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "race-reviewns";
    private static final String EXTENSION = "race-reviewext";
    private static final String REVIEWER_LOGIN = "race-reviewer";

    private static final long COMPLETION_TIMEOUT_SECONDS = 30;

    @Autowired
    LocalRegistryService registry;

    @MockitoSpyBean
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    private long userId;
    private String userLoginName;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var user = new UserData();
            user.setLoginName(REVIEWER_LOGIN);
            em.persist(user);

            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(namespace);
            extension.setActive(true);
            extension.setDeprecated(false);
            extension.setDownloadable(true);
            extension.setPublishedDate(LocalDateTime.now());
            extension.setLastUpdatedDate(LocalDateTime.now());
            em.persist(extension);
            em.flush();

            userId = user.getId();
            userLoginName = user.getLoginName();
        });
    }

    /**
     * The reported race: the same user posts two reviews for the same extension at the same time.
     * Exactly one must succeed; the other must be told it already has a review, not silently succeed
     * too and not fail with a raw constraint-violation error.
     */
    @Test
    void postReview_rejectsTheSecondOfTwoConcurrentReviewsFromTheSameUser() throws Exception {
        var mainThread = Thread.currentThread();
        var raceTriggered = new AtomicBoolean();
        var otherReviewResult = new AtomicReference<ResultJson>();
        var otherReviewFinished = new CountDownLatch(1);
        var otherReviewFailure = new AtomicReference<Throwable>();

        // Let the competing review insert and commit while this one has already seen that no active
        // review exists yet.
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (Thread.currentThread() == mainThread && raceTriggered.compareAndSet(false, true)) {
                var other = new Thread(() -> {
                    setLoggedInUser();
                    try {
                        otherReviewResult.set(postReview());
                    } catch (Throwable t) {
                        otherReviewFailure.set(t);
                    } finally {
                        otherReviewFinished.countDown();
                    }
                }, "concurrent-reviewer");
                other.start();
                otherReviewFinished.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return result;
        }).when(repositories).hasActiveReview(any(), any());

        setLoggedInUser();
        var result = postReview();

        assertThat(raceTriggered).as("the race must have been triggered, otherwise nothing was tested").isTrue();
        assertThat(otherReviewFinished.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(otherReviewFailure.get()).as("the concurrent review must not throw").isNull();

        // Exactly one of the two calls must have succeeded and the other must have been told it
        // already has a review - never both succeeding (the race) and never both failing.
        assertThat(List.of(result, otherReviewResult.get()))
                .extracting(ResultJson::getError)
                .containsExactlyInAnyOrder(null, "You must not submit more than one review for an extension.");
        assertThat(activeReviewCount())
                .as("only one active review must exist for this user and extension")
                .isEqualTo(1L);
    }

    private ResultJson postReview() {
        var review = new ReviewJson();
        review.setRating(5);
        return registry.postReview(review, NAMESPACE, EXTENSION);
    }

    private void setLoggedInUser() {
        var principal = new IdPrincipal(userId, userLoginName, List.of((GrantedAuthority) () -> "github"));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private long activeReviewCount() {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select count(r) from ExtensionReview r "
                                + "where r.extension.name = :name and r.extension.namespace.name = :namespace "
                                + "and r.user.id = :userId and r.active = true",
                        Long.class)
                        .setParameter("name", EXTENSION)
                        .setParameter("namespace", NAMESPACE)
                        .setParameter("userId", userId)
                        .getSingleResult());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionReview r where r.extension.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from UserData u where u.loginName = :login")
                    .setParameter("login", REVIEWER_LOGIN).executeUpdate();
        });
    }
}
