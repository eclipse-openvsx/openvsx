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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.eclipse.openvsx.repositories.RepositoryService;
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

    @Autowired
    RepositoryService repositories;

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

    @Test
    void ordersANamespacePairWithTheMoreDownloadedNamespaceAsTarget() throws ErrorResultException {
        var name = word() + "xy";
        var lookalike = persistNamespace(oneEditFrom(name));
        var target = persistNamespace(name);
        persistNamespaceExtension(target, 50_000, EARLY);

        assertThat(namespacePairOf(target, lookalike, false)).satisfies(pair -> {
            assertThat(pair.target().name()).isEqualTo(target.getName());
            assertThat(pair.target().downloads()).isEqualTo(50_000);
            assertThat(pair.target().activeExtensions()).isEqualTo(1);
            assertThat(pair.target().firstPublished()).isNotNull();
            assertThat(pair.lookalike().name()).isEqualTo(lookalike.getName());
            assertThat(pair.lookalike().firstPublished()).isNull();
            assertThat(pair.editDistance()).isEqualTo(1);
            assertThat(pair.popular()).isTrue();
        });
    }

    @Test
    void popularOnlyLeavesOutPairsWithoutAPopularTargetButCountsThem() throws ErrorResultException {
        var popular = persistNamespacePair(50_000, 0);
        var closeRival = persistNamespacePair(20_000, 5_000);
        var niche = persistNamespacePair(500, 0);

        var all = namespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS);
        var popularOnly = namespacePairs(true, NameSquattingService.NamespaceSort.DOWNLOADS);

        assertThat(targets(all)).contains(popular, closeRival, niche);
        assertThat(targets(popularOnly)).contains(popular).doesNotContain(closeRival, niche);
        assertThat(popularOnly.pairs()).allMatch(NameSquattingJson.NamespacePair::popular);
        assertThat(popularOnly.total()).isEqualTo(all.total());
        assertThat(all.popularTotal()).isEqualTo(popularOnly.popularTotal()).isLessThanOrEqualTo(all.total() - 2);
    }

    @Test
    void sortsNamespacePairsByTargetDownloadsOrByTheNewestLookalike() throws ErrorResultException {
        var olderSquat = persistNamespacePair(90_000, 10, EARLY);
        var newerSquat = persistNamespacePair(30_000, 10, LATE);

        var byDownloads = targets(namespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS));
        var byNewest = targets(namespacePairs(false, NameSquattingService.NamespaceSort.NEWEST));

        assertThat(byDownloads.indexOf(olderSquat)).isLessThan(byDownloads.indexOf(newerSquat));
        assertThat(byNewest.indexOf(newerSquat)).isLessThan(byNewest.indexOf(olderSquat));
    }

    @Test
    void pagesThroughTheNamespacePairs() throws ErrorResultException {
        persistNamespacePair(50_000, 0);
        persistNamespacePair(60_000, 0);
        var all = namespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS);

        var page = service.findNamespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS, 1, 1);

        assertThat(page.pairs()).containsExactly(all.pairs().get(1));
        assertThat(page.offset()).isEqualTo(1);
        assertThat(page.hasMore()).isEqualTo(all.total() > 2);
    }

    @Test
    void allowingNamespacesTakesEffectWhilePairsAreReused() throws ErrorResultException {
        var target = persistNamespacePair(50_000, 0);
        var lookalike = repositories.findNamespace(namespacePairOf(target, false).lookalike().name());

        service.allowNamespaces(
                namespaceAllowRequest(lookalike, repositories.findNamespace(target), "Same publisher"),
                admin);
        em.flush();
        var afterAllow = service.findNamespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS, 0, 100_000);

        assertThat(targets(afterAllow)).doesNotContain(target);
        assertThat(service.findNamespaceAllowlist(0, 100).allows())
                .filteredOn(allow -> allow.namespace().equals(lookalike.getName()))
                .singleElement()
                .satisfies(allow -> {
                    assertThat(allow.otherNamespace()).isEqualTo(target);
                    assertThat(allow.reason()).isEqualTo("Same publisher");
                    assertThat(allow.decidedBy()).isEqualTo(admin.getLoginName());
                });

        var allowId = service.findNamespaceAllowlist(0, 100).allows().stream()
                .filter(allow -> allow.namespace().equals(lookalike.getName()))
                .findFirst()
                .orElseThrow()
                .id();
        service.removeAllow(allowId, admin);
        em.flush();
        var afterRemove = service.findNamespacePairs(false, NameSquattingService.NamespaceSort.DOWNLOADS, 0, 100_000);

        assertThat(targets(afterRemove)).contains(target);
    }

    @Test
    void allowingNamespacesTwiceKeepsOneAllowAndLeavesTheExtensionAllowlistAlone() throws ErrorResultException {
        var target = persistNamespacePair(50_000, 0);
        var lookalike = repositories.findNamespace(namespacePairOf(target, false).lookalike().name());
        var targetNamespace = repositories.findNamespace(target);

        service.allowNamespaces(namespaceAllowRequest(lookalike, targetNamespace, null), admin);
        em.flush();
        var again = service.allowNamespaces(namespaceAllowRequest(targetNamespace, lookalike, null), admin);
        em.flush();

        assertThat(again.getSuccess()).startsWith("Already allowed");
        assertThat(service.findNamespaceAllowlist(0, 100).allows())
                .filteredOn(allow -> allow.otherNamespace().equals(target))
                .hasSize(1);
        assertThat(service.findAllowlist(0, 100).groups())
                .noneMatch(group -> group.extension().namespace().equals(target));
    }

    @Test
    void refusesToAllowNamespacesThatAreUnknownTheSameOrUnalike() {
        var namespace = persistNamespace(word() + "xy");
        var unrelated = persistNamespace(word() + "xy");
        em.flush();

        assertStatus(
                () -> service.allowNamespaces(namespaceAllowRequest(namespace, unrelated, null), admin),
                HttpStatus.BAD_REQUEST);
        assertStatus(
                () -> service.allowNamespaces(namespaceAllowRequest(namespace, namespace, null), admin),
                HttpStatus.BAD_REQUEST);
        assertStatus(
                () -> service.allowNamespaces(
                        new NameSquattingJson.NamespaceAllowRequest(namespace.getName(), "missing" + word(), null),
                        admin),
                HttpStatus.NOT_FOUND);
    }

    private NameSquattingJson.NamespacePairs namespacePairs(
            boolean popularOnly,
            NameSquattingService.NamespaceSort sort
    ) throws ErrorResultException {
        em.flush();
        service.forgetNamespacePairs();
        return service.findNamespacePairs(popularOnly, sort, 0, 100_000);
    }

    private NameSquattingJson.NamespacePair namespacePairOf(Namespace target, Namespace lookalike, boolean popularOnly)
            throws ErrorResultException {
        return namespacePairs(popularOnly, NameSquattingService.NamespaceSort.DOWNLOADS).pairs().stream()
                .filter(pair -> pair.target().name().equals(target.getName()))
                .filter(pair -> pair.lookalike().name().equals(lookalike.getName()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("no pair of " + target.getName() + " and " + lookalike.getName()));
    }

    private NameSquattingJson.NamespacePair namespacePairOf(String target, boolean popularOnly)
            throws ErrorResultException {
        return namespacePairs(popularOnly, NameSquattingService.NamespaceSort.DOWNLOADS).pairs().stream()
                .filter(pair -> pair.target().name().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no pair with target " + target));
    }

    private static List<String> targets(NameSquattingJson.NamespacePairs pairs) {
        return pairs.pairs().stream().map(pair -> pair.target().name()).toList();
    }

    /** Two namespaces one edit apart, the target published early; returns the target's name. */
    private String persistNamespacePair(int targetDownloads, int lookalikeDownloads) {
        return persistNamespacePair(targetDownloads, lookalikeDownloads, LATE);
    }

    private String persistNamespacePair(int targetDownloads, int lookalikeDownloads, LocalDateTime lookalikePublished) {
        var name = word() + "xy";
        var target = persistNamespace(name);
        persistNamespaceExtension(target, targetDownloads, EARLY);
        var lookalike = persistNamespace(oneEditFrom(name));
        if (lookalikeDownloads > 0) {
            persistNamespaceExtension(lookalike, lookalikeDownloads, lookalikePublished);
        }
        return name;
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        namespace.setPublicId(UUID.randomUUID().toString());
        em.persist(namespace);
        return namespace;
    }

    private void persistNamespaceExtension(Namespace namespace, int downloads, LocalDateTime publishedDate) {
        var extension = new Extension();
        extension.setName("ext" + word());
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        extension.setActive(true);
        extension.setDownloadCount(downloads);
        extension.setPublishedDate(publishedDate);
        em.persist(extension);
    }

    /** The name with its first letter swapped for a digit. */
    private static String oneEditFrom(String name) {
        return ThreadLocalRandom.current().nextInt(10) + name.substring(1);
    }

    private static NameSquattingJson.NamespaceAllowRequest namespaceAllowRequest(
            Namespace lookalike,
            Namespace other,
            @Nullable String reason
    ) {
        return new NameSquattingJson.NamespaceAllowRequest(lookalike.getName(), other.getName(), reason);
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(
                ErrorResultException.class,
                exc -> assertThat(exc.getStatus()).isEqualTo(status));
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
