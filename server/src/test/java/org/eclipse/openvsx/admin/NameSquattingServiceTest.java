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
package org.eclipse.openvsx.admin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingJson;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.scanning.NameSimilarityCheckService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test class shares one database, so fixtures carry random words and assertions only look at the
 * extensions a test created.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class NameSquattingServiceTest extends AbstractPostgresContainerTest {

    private static final LocalDateTime EARLY = LocalDateTime.of(2020, 3, 1, 0, 0);
    private static final LocalDateTime MIDDLE = LocalDateTime.of(2023, 3, 1, 0, 0);
    private static final LocalDateTime LATE = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    EntityManager em;

    @Autowired
    NameSquattingService service;

    @Autowired
    NameSimilarityJooqRepository similarity;

    private UserData admin;

    @BeforeEach
    void persistAdmin() {
        admin = new UserData();
        admin.setLoginName("name-squatting-admin-" + UUID.randomUUID());
        admin.setProvider("github");
        em.persist(admin);
    }

    @Test
    void groupsAFlaggedLookalikeUnderTheExtensionPublishedFirst() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        flag(lookalike, LATE.plusDays(1));

        var group = groupOf(original);

        assertThat(group.lookalikes()).singleElement().satisfies(found -> {
            assertThat(found.extension().name()).isEqualTo(lookalike.getName());
            assertThat(found.flaggedAt()).isNotNull();
            assertThat(found.matches()).first().satisfies(match -> {
                assertThat(match.field()).isEqualTo("DISPLAY_NAME");
                assertThat(match.value()).isEqualTo(word + " Formatter");
                assertThat(match.lookalikeValue()).isEqualTo(word + " Formater");
                assertThat(match.exact()).isFalse();
            });
        });
    }

    @Test
    void groupsTheSameWayWhenTheExtensionPublishedFirstWasFlagged() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        flag(original, LATE.plusDays(1));

        var group = groupOf(original);

        assertThat(group.lookalikes()).singleElement().satisfies(found -> {
            assertThat(found.extension().name()).isEqualTo(lookalike.getName());
            assertThat(found.matches().getFirst().value()).isEqualTo(word + " Formatter");
            assertThat(found.matches().getFirst().lookalikeValue()).isEqualTo(word + " Formater");
        });
    }

    @Test
    void listsALookalikeOnceUnderTheEarliestExtensionItResembles() throws ErrorResultException {
        var word = word();
        var earliest = persistExtension(word + " Tools", EARLY);
        var later = persistExtension(word + " Tools", MIDDLE);
        var lookalike = persistExtension(word + " Tools", LATE);
        flag(lookalike, LATE.plusDays(1));
        em.flush();

        var placements = service.findCollisions(0, 100).groups().stream()
                .filter(
                        group -> group.lookalikes().stream()
                                .anyMatch(found -> found.extension().name().equals(lookalike.getName())))
                .map(group -> group.anchor().name())
                .toList();

        assertThat(placements).containsExactly(earliest.getName());
        assertThat(placements).doesNotContain(later.getName());
    }

    @Test
    void allowedPairsLeaveTheCollisionsAndJoinTheAllowlist() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        flag(lookalike, LATE.plusDays(1));
        em.flush();

        service.allow(allowRequest(lookalike, original, "Official fork"), admin);
        em.flush();

        assertThat(lookalikeNames(service.findCollisions(0, 100))).doesNotContain(lookalike.getName());
        assertThat(allowlistGroupOf(lookalike).allows()).singleElement().satisfies(allow -> {
            assertThat(allow.other().name()).isEqualTo(original.getName());
            assertThat(allow.field()).isEqualTo("DISPLAY_NAME");
            assertThat(allow.value()).isEqualTo(word + " Formater");
            assertThat(allow.otherValue()).isEqualTo(word + " Formatter");
            assertThat(allow.renamed()).isFalse();
            assertThat(allow.reason()).isEqualTo("Official fork");
            assertThat(allow.decidedBy()).isEqualTo(admin.getLoginName());
        });
    }

    @Test
    void allowingTwiceKeepsOneAllow() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        em.flush();

        service.allow(allowRequest(lookalike, original, null), admin);
        em.flush();
        var again = service.allow(allowRequest(original, lookalike, null), admin);
        em.flush();

        assertThat(again.getSuccess()).startsWith("Already allowed");
        assertThat(allowlistGroupOf(lookalike).allows()).hasSize(1);
    }

    @Test
    void refusesToAllowExtensionsThatDoNotResembleEachOther() {
        var original = persistExtension(word() + " Formatter", EARLY);
        var unrelated = persistExtension(word() + " Linter", LATE);
        em.flush();

        assertThatThrownBy(() -> service.allow(allowRequest(unrelated, original, null), admin))
                .isInstanceOfSatisfying(
                        ErrorResultException.class,
                        exc -> assertThat(exc.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void removingAnAllowReportsThePairAgain() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        flag(lookalike, LATE.plusDays(1));
        em.flush();
        service.allow(allowRequest(lookalike, original, null), admin);
        em.flush();

        service.removeAllow(allowlistGroupOf(lookalike).allows().getFirst().id(), admin);

        assertThat(groupOf(original).lookalikes()).extracting(found -> found.extension().name())
                .containsExactly(lookalike.getName());
    }

    @Test
    void marksAnAllowWhoseNamesChangedSince() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Formatter", EARLY);
        var lookalike = persistExtension(word + " Formater", LATE);
        em.flush();
        service.allow(allowRequest(lookalike, original, null), admin);
        lookalike.getVersions().getFirst().setActive(false);
        persistVersion(lookalike, "2.0.0", word + " Formatter Pro");
        em.flush();

        assertThat(allowlistGroupOf(lookalike).allows()).singleElement()
                .satisfies(allow -> assertThat(allow.renamed()).isTrue());
    }

    @Test
    void findsDisplayNamesSharedAcrossNamespacesWithoutAFlag() throws ErrorResultException {
        var word = word();
        var original = persistExtension(word + " Tools", EARLY);
        var grandfathered = persistExtension(word + " TOOLS", MIDDLE);
        var flagged = persistExtension(word() + " Other", LATE);
        flag(flagged, LATE.plusDays(1));
        em.flush();

        var candidates = similarity.findCandidates(NameSimilarityCheckService.CHECK_TYPE, 0, 100_000, TIMEOUT);

        var ids = candidates.stream().map(NameSimilarityJooqRepository.Candidate::extensionId).toList();
        assertThat(ids).contains(original.getId(), grandfathered.getId(), flagged.getId());
        assertThat(ids.indexOf(flagged.getId())).isLessThan(ids.indexOf(original.getId()));
        assertThat(candidates).filteredOn(candidate -> candidate.extensionId() == original.getId())
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.flaggedAt()).isNull());
    }

    @Test
    void pagesContinueFromTheFlaggedCandidatesIntoSharedDisplayNames() {
        var word = word();
        persistExtension(word + " Tools", EARLY);
        persistExtension(word + " Tools", MIDDLE);
        flag(persistExtension(word() + " Other", LATE), LATE.plusDays(1));
        flag(persistExtension(word() + " Else", LATE), LATE.plusDays(2));
        em.flush();
        var all = similarity.findCandidates(NameSimilarityCheckService.CHECK_TYPE, 0, 100_000, TIMEOUT);
        var flaggedCount = (int) all.stream().filter(candidate -> candidate.flaggedAt() != null).count();

        var acrossTheBoundary = similarity.findCandidates(
                NameSimilarityCheckService.CHECK_TYPE,
                flaggedCount - 1,
                2,
                TIMEOUT);
        var pastIt = similarity.findCandidates(
                NameSimilarityCheckService.CHECK_TYPE,
                flaggedCount + 1,
                1,
                TIMEOUT);

        assertThat(acrossTheBoundary).containsExactly(all.get(flaggedCount - 1), all.get(flaggedCount));
        assertThat(acrossTheBoundary.get(1).flaggedAt()).isNull();
        assertThat(pastIt).containsExactly(all.get(flaggedCount + 1));
    }

    @Test
    void recognizesAStatementCancelledByItsTimeout() {
        var cancelled = new RuntimeException(
                new java.sql.SQLException("canceling statement due to statement timeout", "57014"));

        assertThat(NameSquattingService.isStatementTimeout(cancelled)).isTrue();
        assertThat(
                NameSquattingService
                        .isStatementTimeout(new RuntimeException(new java.sql.SQLException("other", "23505"))))
                .isFalse();
    }

    private NameSquattingJson.Group groupOf(Extension anchor) throws ErrorResultException {
        em.flush();
        return service.findCollisions(0, 100).groups().stream()
                .filter(group -> group.anchor().name().equals(anchor.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no group for " + anchor.getName()));
    }

    private NameSquattingJson.AllowlistGroup allowlistGroupOf(Extension extension) {
        return service.findAllowlist(0, 100).groups().stream()
                .filter(group -> group.extension().name().equals(extension.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no allowlist group for " + extension.getName()));
    }

    private static List<String> lookalikeNames(NameSquattingJson.Collisions collisions) {
        return collisions.groups().stream()
                .flatMap(group -> group.lookalikes().stream())
                .map(found -> found.extension().name())
                .toList();
    }

    private static NameSquattingJson.AllowRequest allowRequest(
            Extension lookalike,
            Extension other,
            @Nullable String reason
    ) {
        return new NameSquattingJson.AllowRequest(
                lookalike.getNamespace().getName(),
                lookalike.getName(),
                other.getNamespace().getName(),
                other.getName(),
                reason);
    }

    /** Records a NAME_SIMILARITY flag on a scan of the extension, as the publish check would. */
    private void flag(Extension extension, LocalDateTime detectedAt) {
        var scan = new ExtensionScan();
        scan.setNamespaceName(extension.getNamespace().getName());
        scan.setExtensionName(extension.getName());
        scan.setExtensionVersion("1.0.0");
        scan.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        scan.setUniversalTargetPlatform(true);
        scan.setPublisher("name-squatting-publisher");
        scan.setStartedAt(detectedAt);
        scan.setStatus(ScanStatus.PASSED);
        em.persist(scan);

        var failure = new ExtensionValidationFailure();
        failure.setScan(scan);
        failure.setCheckType(NameSimilarityCheckService.CHECK_TYPE);
        failure.setRuleName("DISPLAY_NAME");
        failure.setValidationFailureReason("flagged by the test");
        failure.setEnforced(false);
        failure.setDetectedAt(detectedAt);
        em.persist(failure);
    }

    /** An extension in a namespace of its own, so every pair a test makes spans two namespaces. */
    private Extension persistExtension(String displayName, LocalDateTime publishedDate) {
        var namespace = new Namespace();
        namespace.setName("ns" + word());
        namespace.setPublicId(UUID.randomUUID().toString());
        em.persist(namespace);

        var extension = new Extension();
        extension.setName("ext" + word());
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        extension.setActive(true);
        extension.setPublishedDate(publishedDate);
        em.persist(extension);
        persistVersion(extension, "1.0.0", displayName);
        return extension;
    }

    private void persistVersion(Extension extension, String version, String displayName) {
        var extVersion = new ExtensionVersion();
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        extVersion.setPublishedBy(admin);
        extVersion.setActive(true);
        extVersion.setDisplayName(displayName);
        extVersion.setTimestamp(extension.getPublishedDate());
        em.persist(extVersion);
        extension.getVersions().add(extVersion);
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
}
