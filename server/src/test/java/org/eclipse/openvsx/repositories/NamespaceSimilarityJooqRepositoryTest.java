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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.NamespaceSimilarityJooqRepository.Pair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test class shares one database, and the lookup reads all of it, so fixtures carry random words
 * that nothing else contains and assertions only look at the namespaces a test created.
 */
@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class NamespaceSimilarityJooqRepositoryTest extends AbstractPostgresContainerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    NamespaceSimilarityJooqRepository repo;

    @Autowired
    DSLContext dsl;

    @Autowired
    EntityManager em;

    @Test
    void allowedEditsGrowWithTheLongerName() {
        assertThat(NamespaceSimilarityJooqRepository.allowedEdits(4)).isZero();
        assertThat(NamespaceSimilarityJooqRepository.allowedEdits(5)).isEqualTo(1);
        assertThat(NamespaceSimilarityJooqRepository.allowedEdits(8)).isEqualTo(1);
        assertThat(NamespaceSimilarityJooqRepository.allowedEdits(9)).isEqualTo(2);
    }

    @Test
    void midLengthNamesMatchWithinOneEdit() {
        var word = word(8);
        var original = withExtension(namespace(word));
        var oneEdit = namespace(replaceAt(word, 3));
        var twoEdits = namespace(replaceAt(replaceAt(word, 3), 6));

        var pair = pairOf(original, oneEdit);
        assertThat(pair).hasValueSatisfying(found -> {
            assertThat(found.editDistance()).isEqualTo(1);
            assertThat(found.separatorOnly()).isFalse();
            assertThat(found.similarity()).isBetween(0f, 1f);
        });
        assertThat(pairOf(original, twoEdits)).isEmpty();
    }

    @Test
    void longNamesMatchWithinTwoEdits() {
        var word = word(12);
        var original = withExtension(namespace(word));
        var twoEdits = namespace(replaceAt(replaceAt(word, 2), 9));
        var threeEdits = namespace(replaceAt(replaceAt(replaceAt(word, 2), 6), 9));

        assertThat(pairOf(original, twoEdits))
                .hasValueSatisfying(found -> assertThat(found.editDistance()).isEqualTo(2));
        assertThat(pairOf(original, threeEdits)).isEmpty();
    }

    @Test
    void comparesNamesIgnoringCase() {
        var word = word(8);
        var original = withExtension(namespace(word.toUpperCase()));
        var lookalike = namespace(replaceAt(word, 0));

        assertThat(pairOf(original, lookalike))
                .hasValueSatisfying(found -> assertThat(found.editDistance()).isEqualTo(1));
    }

    @Test
    void shortNamesOnlyMatchWhenTheyDifferInSeparators() {
        var word = word(4);
        var original = withExtension(namespace(word));
        var oneEdit = namespace(replaceAt(word, 1));
        var separated = namespace(word.charAt(0) + "-" + word.charAt(1) + "_" + word.charAt(2) + "~" + word.charAt(3));

        assertThat(pairOf(original, oneEdit)).isEmpty();
        assertThat(pairOf(original, separated)).hasValueSatisfying(found -> {
            assertThat(found.separatorOnly()).isTrue();
            assertThat(found.editDistance()).isEqualTo(3);
        });
    }

    @Test
    void reportsEachPairOnceWithTheLowerIdFirst() {
        var word = word(10);
        var original = withExtension(namespace(word));
        var lookalike = namespace(replaceAt(word, 4));

        var pairs = pairsBetween(original, lookalike);

        assertThat(pairs).singleElement().satisfies(pair -> {
            assertThat(pair.first().id()).isEqualTo(original.getId());
            assertThat(pair.second().id()).isEqualTo(lookalike.getId());
            assertThat(pair.second().name()).isEqualTo(lookalike.getName());
        });
    }

    @Test
    void sumsTheActiveExtensionsOfEachSide() {
        var word = word(10);
        var original = namespace(word);
        persistExtension(original, "one", true, 1500, LocalDateTime.of(2023, 5, 1, 0, 0));
        persistExtension(original, "two", true, 500, LocalDateTime.of(2022, 3, 1, 0, 0));
        persistExtension(original, "gone", false, 100_000, LocalDateTime.of(2020, 1, 1, 0, 0));
        var lookalike = namespace(replaceAt(word, 4));

        assertThat(pairOf(original, lookalike)).hasValueSatisfying(pair -> {
            assertThat(pair.first().downloads()).isEqualTo(2000);
            assertThat(pair.first().activeExtensions()).isEqualTo(2);
            assertThat(pair.first().firstPublished()).isEqualTo(LocalDateTime.of(2022, 3, 1, 0, 0));
            assertThat(pair.second().downloads()).isZero();
            assertThat(pair.second().activeExtensions()).isZero();
            assertThat(pair.second().firstPublished()).isNull();
        });
    }

    @Test
    void leavesOutPairsWithoutAnActiveExtensionOnEitherSide() {
        var word = word(10);
        var original = namespace(word);
        persistExtension(original, "gone", false, 10, LocalDateTime.of(2020, 1, 1, 0, 0));
        var lookalike = namespace(replaceAt(word, 4));

        assertThat(pairOf(original, lookalike)).isEmpty();
    }

    @Test
    void flagsPairsSharingAMember() {
        var word = word(10);
        var original = withExtension(namespace(word));
        var sibling = namespace(replaceAt(word, 4));
        var stranger = namespace(replaceAt(word, 7));
        var user = persistUser();
        persistMembership(original, user, NamespaceMembership.ROLE_OWNER);
        persistMembership(sibling, user, NamespaceMembership.ROLE_CONTRIBUTOR);
        persistMembership(stranger, persistUser(), NamespaceMembership.ROLE_OWNER);

        assertThat(pairOf(original, sibling)).hasValueSatisfying(pair -> assertThat(pair.sharedMember()).isTrue());
        assertThat(pairOf(original, stranger)).hasValueSatisfying(pair -> assertThat(pair.sharedMember()).isFalse());
    }

    @Test
    void probesTheTrigramIndex() {
        NamespaceSimilarityJooqRepository.applyThreshold(dsl);
        // The fixture table is nearly empty, so the planner would rather read it whole.
        dsl.execute("SET LOCAL enable_seqscan = off");

        var plan = dsl.explain(NamespaceSimilarityJooqRepository.pairsQuery(dsl)).plan();

        var lines = plan.lines().toList();
        var scan = -1;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Bitmap Index Scan on namespace_name_trgm_idx")) {
                scan = i;
            }
        }
        assertThat(scan).as("plan reads namespace_name_trgm_idx:%n%s", plan).isNotNegative();
        assertThat(lines.get(scan + 1)).as("plan searches the index with %%:%n%s", plan)
                .contains("Index Cond:")
                .contains(" % ");
    }

    @Test
    void putsTheStatementTimeoutBackAfterTheLookup() {
        dsl.execute("SET LOCAL statement_timeout = '42s'");

        repo.findSimilarPairs(TIMEOUT);

        assertThat(
                dsl.select(DSL.function("current_setting", String.class, DSL.inline("statement_timeout")))
                        .fetchOne(0, String.class))
                .isEqualTo("42s");
    }

    @Test
    void rejectsATimeoutThatIsNotPositive() {
        assertThatThrownBy(() -> repo.findSimilarPairs(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private Optional<Pair> pairOf(Namespace first, Namespace second) {
        var pairs = pairsBetween(first, second);
        assertThat(pairs).hasSizeLessThanOrEqualTo(1);
        return pairs.stream().findFirst();
    }

    private List<Pair> pairsBetween(Namespace first, Namespace second) {
        em.flush();
        var ids = List.of(first.getId(), second.getId());
        return repo.findSimilarPairs(TIMEOUT).stream()
                .filter(pair -> ids.contains(pair.first().id()) && ids.contains(pair.second().id()))
                .toList();
    }

    /** Random lowercase letters: as good as unique within the shared database from 8 on. */
    private static String word(int length) {
        var random = ThreadLocalRandom.current();
        var word = new StringBuilder();
        for (var i = 0; i < length; i++) {
            word.append((char) ('a' + random.nextInt(26)));
        }
        return word.toString();
    }

    /** {@code word} with the letter at {@code index} swapped for a digit, so it is one edit away. */
    private static String replaceAt(String word, int index) {
        return word.substring(0, index) + ThreadLocalRandom.current().nextInt(10) + word.substring(index + 1);
    }

    private Namespace namespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        namespace.setPublicId(UUID.randomUUID().toString());
        em.persist(namespace);
        return namespace;
    }

    private Namespace withExtension(Namespace namespace) {
        persistExtension(namespace, "ext", true, 0, LocalDateTime.of(2024, 1, 1, 0, 0));
        return namespace;
    }

    private void persistExtension(
            Namespace namespace,
            String name,
            boolean active,
            int downloads,
            LocalDateTime published
    ) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        extension.setActive(active);
        extension.setDownloadCount(downloads);
        extension.setPublishedDate(published);
        em.persist(extension);
    }

    private UserData persistUser() {
        var user = new UserData();
        user.setLoginName("namespace-similarity-" + word(8));
        user.setProvider("github");
        em.persist(user);
        return user;
    }

    private void persistMembership(Namespace namespace, UserData user, String role) {
        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(role);
        em.persist(membership);
    }
}
