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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Probe;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test class shares one database, and these lookups scan all of it, so fixtures carry random
 * words that nothing else contains and assertions only look at the extensions a test created.
 */
@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class NameSimilarityJooqRepositoryTest extends AbstractPostgresContainerTest {

    private static final LocalDateTime PUBLISHED = LocalDateTime.of(2024, 1, 1, 0, 0);

    @Autowired
    NameSimilarityJooqRepository repo;

    @Autowired
    DSLContext dsl;

    @Autowired
    EntityManager em;

    private @Nullable UserData publisher;

    @Test
    void reportsNearDisplayNameWithItsScore() {
        var word = word();
        var existing = persistExtension(namespace(), word + "-formatter", word + " Formatter Pro");

        var matches = find(probe("other", "x" + word(), word + " Formatter", 0.5f), existing);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.field()).isEqualTo(NameSimilarityField.DISPLAY_NAME);
            assertThat(match.value()).isEqualTo(word + " Formatter Pro");
            assertThat(match.probeValue()).isEqualTo(word + " Formatter");
            assertThat(match.exact()).isFalse();
            assertThat(match.similarity()).isEqualTo(sqlSimilarity(word + " formatter pro", word + " formatter"));
            assertThat(match.similarity()).isBetween(0.5f, 0.99f);
        });
    }

    @Test
    void exactDisplayNameIgnoresCaseAndSurroundingUnicodeWhitespace() {
        var word = word();
        var existing = persistExtension(namespace(), word, "\u00A0" + word.toUpperCase() + " Tools\u3000");

        var matches = find(probe("other", "x" + word(), " " + word + " tools", 0.7f), existing);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.exact()).isTrue();
            assertThat(match.similarity()).isEqualTo(1.0f);
        });
    }

    @Test
    void repeatedWordScoresOneWithoutBeingExact() {
        var word = word();
        var existing = persistExtension(namespace(), "x" + word(), word + " " + word);

        var matches = find(probe("other", "x" + word(), word, 0.7f), existing);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.similarity()).isEqualTo(1.0f);
            assertThat(match.exact()).isFalse();
        });
    }

    @Test
    void neverMatchesWithinTheProbedNamespaceWhateverItsCase() {
        var word = word();
        var namespace = namespace();
        var existing = persistExtension(namespace, word, word + " Tools");

        var matches = find(probe(namespace.getName().toUpperCase(), word, word + " Tools", 0.5f), existing);

        assertThat(matches).isEmpty();
    }

    @Test
    void ignoresInactiveExtensionsAndInactiveVersions() {
        var word = word();
        var inactiveExtension = persistExtension(namespace(), word + "-one", word + " Tools");
        inactiveExtension.setActive(false);
        var inactiveVersion = persistExtension(namespace(), "x" + word(), word + " Tools");
        inactiveVersion.getVersions().getFirst().setActive(false);

        var matches = find(probe("other", "y" + word(), word + " Tools", 0.5f), inactiveExtension, inactiveVersion);

        assertThat(matches).isEmpty();
    }

    @Test
    void versionsSharingADisplayNameMatchOnce() {
        var word = word();
        var existing = persistExtension(namespace(), "x" + word(), word + " Tools");
        persistVersion(existing, "2.0.0", word + " Tools");
        persistVersion(existing, "3.0.0", word + " TOOLS");

        var matches = find(probe("other", "y" + word(), word + " Tools", 0.5f), existing);

        assertThat(matches).singleElement().extracting(Match::exact).isEqualTo(true);
    }

    @Test
    void leavesOutMatchesBelowTheThreshold() {
        var word = word();
        var existing = persistExtension(namespace(), "x" + word(), word + " Formatter Pro");

        var matches = find(probe("other", "y" + word(), word + " Formatter", 0.95f), existing);

        assertThat(matches).isEmpty();
    }

    @Test
    void shortNamesOnlyMatchExactly() {
        var word = word().substring(0, 3);
        var exact = persistExtension(namespace(), word, word.toUpperCase());
        var near = persistExtension(namespace(), word.substring(0, 2), word.substring(0, 2));

        var matches = find(new Probe("other", word, List.of(word), 0.1f, 0.1f, 4, 50), exact, near);

        assertThat(matches).isNotEmpty().allSatisfy(match -> {
            assertThat(match.extensionId()).isEqualTo(exact.getId());
            assertThat(match.exact()).isTrue();
        });
        assertThat(matches).extracting(Match::field)
                .containsExactlyInAnyOrder(NameSimilarityField.NAME, NameSimilarityField.DISPLAY_NAME);
    }

    @Test
    void reportsNearExtensionNameWithNamespaceSimilarity() {
        var word = word();
        var namespace = persistNamespace("acme" + word);
        var existing = persistExtension(namespace, word + "-vscode", "Unrelated " + word());

        var matches = find(probe("acme" + word + "x", word + "-vscodee", null, 0.5f), existing);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.field()).isEqualTo(NameSimilarityField.NAME);
            assertThat(match.value()).isEqualTo(word + "-vscode");
            assertThat(match.exact()).isFalse();
            assertThat(match.similarity()).isEqualTo(sqlSimilarity(word + "-vscode", word + "-vscodee"));
            assertThat(match.namespaceSimilarity()).isEqualTo(sqlSimilarity("acme" + word, "acme" + word + "x"));
        });
    }

    @Test
    void displayNameLookupUsesTheTrigramIndex() {
        var probe = probe("other", "x" + word(), word() + " Tools", 0.7f);
        NameSimilarityJooqRepository.applyThreshold(dsl, probe);
        disablePlansThatBypassTrigramIndexes();

        var plan = dsl.explain(NameSimilarityJooqRepository.displayNamesQuery(dsl, probe, word() + " Tools")).plan();

        assertSearchesTrigramIndex(plan, "extension_version_display_name_trgm_idx");
    }

    @Test
    void extensionNameLookupUsesTheTrigramIndex() {
        var probe = probe("other", "x" + word(), null, 0.7f);
        NameSimilarityJooqRepository.applyThreshold(dsl, probe);
        disablePlansThatBypassTrigramIndexes();

        var plan = dsl.explain(NameSimilarityJooqRepository.namesQuery(dsl, probe)).plan();

        assertSearchesTrigramIndex(plan, "extension_name_trgm_idx");
    }

    @Test
    void groupsDisplayNamesSharedAcrossNamespaces() {
        var word = word();
        var first = persistExtension(namespace(), "x" + word(), word.toUpperCase() + " Shared");
        var second = persistExtension(namespace(), "x" + word(), "\u2002" + word + " shared");
        var soloNamespace = namespace();
        persistExtension(soloNamespace, "x" + word(), word + " Solo");
        persistExtension(soloNamespace, "x" + word(), word + " Solo");
        em.flush();

        var groups = repo.findSharedDisplayNames();

        assertThat(groups).filteredOn(group -> group.normalizedDisplayName().equals(word + " shared"))
                .singleElement()
                .satisfies(
                        group -> assertThat(group.extensionIds())
                                .containsExactlyInAnyOrder(first.getId(), second.getId()));
        assertThat(groups).noneMatch(group -> group.normalizedDisplayName().equals(word + " solo"));
    }

    @Test
    void javaNormalizationAgreesWithTheSqlExpression() {
        var samples = new ArrayList<String>();
        for (var c : NameSimilarityJooqRepository.DISPLAY_NAME_TRIM_CHARS.toCharArray()) {
            samples.add(c + "Ab\u00A0C" + c + c);
        }
        samples.add("\tKept Tab\t");

        for (var sample : samples) {
            var sql = dsl.select(NameSimilarityJooqRepository.normalizedDisplayName(DSL.val(sample)))
                    .fetchOne(0, String.class);
            assertThat(NameSimilarityJooqRepository.normalizeDisplayName(sample))
                    .as("normalizing %s", sample)
                    .isEqualTo(sql);
        }
    }

    @Test
    void trimLiteralIsTheOneTheIndexMigrationUses() throws IOException {
        var migration = new ClassPathResource("db/migration/V1_87__Name_Similarity_Indexes.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("btrim(display_name, " + NameSimilarityJooqRepository.DISPLAY_NAME_TRIM_LITERAL + ")");
    }

    @Test
    void probeRejectsThresholdsOutsideTheUnitInterval() {
        assertThatThrownBy(() -> new Probe("ns", "ext", List.of(), 0f, 0.7f, 4, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Probe("ns", "ext", List.of(), 0.7f, 1.5f, 4, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Match> find(Probe probe, Extension... ofInterest) {
        em.flush();
        Set<Long> ids = Arrays.stream(ofInterest).map(Extension::getId).collect(Collectors.toSet());
        return repo.findSimilar(probe).stream().filter(match -> ids.contains(match.extensionId())).toList();
    }

    private static Probe probe(
            String namespaceName,
            String extensionName,
            @Nullable String displayName,
            float threshold
    ) {
        Collection<String> displayNames = displayName == null ? List.of() : List.of(displayName);
        return new Probe(namespaceName, extensionName, displayNames, threshold, threshold, 4, 50);
    }

    /**
     * The fixture tables are nearly empty, so the planner would rather read them whole, by a sequential
     * scan or a full scan of any B-tree. GIN indexes are only read through bitmap scans, so with both of
     * those off the plan names a trigram index only if the query's expression matches it.
     */
    private void disablePlansThatBypassTrigramIndexes() {
        dsl.execute("SET LOCAL enable_seqscan = off");
        dsl.execute("SET LOCAL enable_indexscan = off");
    }

    /**
     * The index has to be searched with the % comparison. Merely reading it does not show the expressions
     * match: being partial on active, it also stands in for a scan of the active rows, with % left as a
     * filter on every one of them.
     */
    private static void assertSearchesTrigramIndex(String plan, String index) {
        var lines = plan.lines().toList();
        var scan = -1;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Bitmap Index Scan on " + index)) {
                scan = i;
            }
        }
        assertThat(scan).as("plan reads %s:%n%s", index, plan).isNotNegative();
        assertThat(lines.get(scan + 1)).as("plan searches %s with %%:%n%s", index, plan)
                .contains("Index Cond:")
                .contains(" % ");
    }

    private float sqlSimilarity(String first, String second) {
        return dsl.select(DSL.function("similarity", Float.class, DSL.val(first), DSL.val(second)))
                .fetchOne(0, Float.class);
    }

    /** Eight random letters: their trigrams are as good as unique within the shared database. */
    private static String word() {
        var random = ThreadLocalRandom.current();
        var word = new StringBuilder();
        for (var i = 0; i < 8; i++) {
            word.append((char) ('a' + random.nextInt(26)));
        }
        return word.toString();
    }

    private Namespace namespace() {
        return persistNamespace("ns" + word());
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        namespace.setPublicId(UUID.randomUUID().toString());
        em.persist(namespace);
        return namespace;
    }

    private Extension persistExtension(Namespace namespace, String name, String displayName) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        extension.setActive(true);
        extension.setPublishedDate(PUBLISHED);
        em.persist(extension);
        persistVersion(extension, "1.0.0", displayName);
        return extension;
    }

    private void persistVersion(Extension extension, String version, String displayName) {
        var extVersion = new ExtensionVersion();
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        extVersion.setPublishedBy(publisher());
        extVersion.setActive(true);
        extVersion.setDisplayName(displayName);
        extVersion.setTimestamp(PUBLISHED);
        em.persist(extVersion);
        extension.getVersions().add(extVersion);
    }

    private UserData publisher() {
        if (publisher == null) {
            publisher = new UserData();
            publisher.setLoginName("name-similarity-" + word());
            publisher.setProvider("github");
            em.persist(publisher);
        }
        return publisher;
    }
}
