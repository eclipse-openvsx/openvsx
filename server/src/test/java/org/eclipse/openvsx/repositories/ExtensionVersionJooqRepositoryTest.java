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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_VERSION;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;
import static org.eclipse.openvsx.jooq.Tables.SIGNATURE_KEY_PAIR;

/**
 * {@code isDeleteAllActiveVersions} builds its "all active versions" count query by chaining
 * {@code .and(...)} directly off {@code .join(...).on(...)}, which attaches those predicates to the
 * JOIN's ON clause rather than a WHERE clause - unlike the "actual" count query right below it, which
 * uses {@code .where(...)}. For the inner joins used here that placement doesn't change the result, but
 * it is a latent trap (e.g. if a join here were ever widened to a LEFT JOIN). These tests pin down the
 * method's current, correct behaviour - including that the "all" count is properly scoped to the given
 * namespace/extension and excludes inactive versions - so a future change to the join structure is
 * caught if it ever changes the result.
 */
@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExtensionVersionJooqRepositoryTest extends AbstractPostgresContainerTest {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionVersionJooqRepositoryTest.class);

    @Autowired
    ExtensionVersionJooqRepository repo;

    @Autowired
    EntityManager em;

    @Autowired
    DSLContext dsl;

    private UserData owner;

    @BeforeEach
    void setUp() {
        owner = new UserData();
        owner.setLoginName("jooq-repo-test-owner");
        em.persist(owner);
        em.flush();
    }

    // The PUBLISHED_WITH_TT column is selected by several findLatest(...) queries, but was never
    // actually read back into ExtensionVersion#publishedWithTt (and, for the two-stage "latest"
    // subquery variants, wasn't even selected by the outer query) - so the trusted-publisher flag
    // silently came back null regardless of what was stored. These pin down that it now round-trips
    // through each of the three findLatest(...) overloads that build a full ExtensionVersion off a
    // "latest" subquery.
    @Test
    void mapsPublishedWithTtOnFindLatestForNamespaceAndExtension() {
        var extension = persistExtension("ns-tt-1", "ext-tt-1");
        persistVersionWithTokenType(extension, "1.0.0", PersonalAccessTokenType.TPT);

        var extVersion = repo.findLatest(owner, "ns-tt-1", "ext-tt-1");

        assertThat(extVersion.getPublishedWithTt()).isEqualTo(PersonalAccessTokenType.TPT);
    }

    @Test
    void mapsPublishedWithTtOnFindLatestByExtensionIds() {
        var extension = persistExtension("ns-tt-2", "ext-tt-2");
        persistVersionWithTokenType(extension, "1.0.0", PersonalAccessTokenType.TPT);

        var versions = repo.findLatest(List.of(extension.getId()));

        assertThat(versions).singleElement()
                .extracting(ExtensionVersion::getPublishedWithTt)
                .isEqualTo(PersonalAccessTokenType.TPT);
    }

    // TableFieldMapper remaps a field belonging to the original EXTENSION_VERSION table onto the
    // "latest" derived table's equivalent field, so toExtensionVersionCommon's row.get(...) calls
    // resolve against the row that is actually returned rather than relying on Record.get(...) to
    // guess a match for an ambiguous column name shared with e.g. NAMESPACE.ID/EXTENSION.ID, which
    // are also selected here. Persisting several earlier versions of the same extension first pushes
    // the extension_version id sequence well ahead of the namespace/extension ids, so a wrong column
    // being picked up would return a clearly different (wrong) id rather than coincidentally matching.
    @Test
    void findLatestByExtensionIdsMapsTheExtensionVersionIdRatherThanAnUnrelatedSameNamedColumn() {
        var extension = persistExtension("ns-tt-id", "ext-tt-id");
        for (var i = 0; i < 20; i++) {
            persistVersion(extension, "0.0." + i, TargetPlatform.NAME_UNIVERSAL, true);
        }
        var latest = new ExtensionVersion();
        latest.setExtension(extension);
        latest.setVersion("1.0.0");
        latest.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        latest.setActive(true);
        latest.setPublishedBy(owner);
        em.persist(latest);
        em.flush();

        var versions = repo.findLatest(List.of(extension.getId()));

        assertThat(versions).singleElement().extracting(ExtensionVersion::getId).isEqualTo(latest.getId());
    }

    @Test
    void mapsPublishedWithTtOnFindLatestByUser() {
        var extension = persistExtension("ns-tt-3", "ext-tt-3");
        persistVersionWithTokenType(extension, "1.0.0", PersonalAccessTokenType.TPT);

        var versions = repo.findLatest(owner);

        assertThat(versions).singleElement()
                .extracting(ExtensionVersion::getPublishedWithTt)
                .isEqualTo(PersonalAccessTokenType.TPT);
    }

    // A version published without a token (e.g. via a logged-in web session) must not be reported
    // as trusted-publisher just because the column is nullable.
    @Test
    void publishedWithTtIsNullWhenNotPublishedViaAToken() {
        var extension = persistExtension("ns-tt-4", "ext-tt-4");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        var extVersion = repo.findLatest(owner, "ns-tt-4", "ext-tt-4");

        assertThat(extVersion.getPublishedWithTt()).isNull();
    }

    @Test
    void returnsTrueWhenDeletingTheOnlyActiveVersion() {
        var extension = persistExtension("ns1", "ext1");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        var deletesAll = repo.isDeleteAllActiveVersions(
                "ns1",
                "ext1",
                TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"));

        assertThat(deletesAll).isTrue();
    }

    @Test
    void returnsFalseWhenOtherActiveVersionsRemain() {
        var extension = persistExtension("ns2", "ext2");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);
        persistVersion(extension, "2.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        var deletesAll = repo.isDeleteAllActiveVersions(
                "ns2",
                "ext2",
                TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"));

        assertThat(deletesAll)
                .as("deleting only one of two active versions must not report 'delete all'")
                .isFalse();
    }

    @Test
    void returnsTrueWhenDeletingAllActiveVersionsAcrossTargetPlatforms() {
        var extension = persistExtension("ns3", "ext3");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_WIN32_X64, true);

        var deletesAll = repo.isDeleteAllActiveVersions(
                "ns3",
                "ext3",
                TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"),
                TargetPlatformVersion.of(TargetPlatform.NAME_WIN32_X64, "1.0.0"));

        assertThat(deletesAll).isTrue();
    }

    @Test
    void ignoresAlreadyInactiveVersionsWhenCountingAll() {
        var extension = persistExtension("ns4", "ext4");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);
        persistVersion(extension, "0.9.0", TargetPlatform.NAME_UNIVERSAL, false);

        var deletesAll = repo.isDeleteAllActiveVersions(
                "ns4",
                "ext4",
                TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"));

        assertThat(deletesAll)
                .as("an already-inactive version must not count toward the 'all active versions' total")
                .isTrue();
    }

    @Test
    void doesNotCountActiveVersionsOfOtherExtensionsOrNamespaces() {
        var ns5 = persistNamespace("ns5");
        var target = persistExtension(ns5, "ext5");
        persistVersion(target, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        // Same extension name in a different namespace, and a different extension in the same
        // namespace - neither must leak into the "all" count for ns5/ext5.
        var otherNamespace = persistNamespace("other-ns");
        var sameNameOtherNamespace = persistExtension(otherNamespace, "ext5");
        persistVersion(sameNameOtherNamespace, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        var otherExtensionSameNamespace = persistExtension(ns5, "other-ext");
        persistVersion(otherExtensionSameNamespace, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        var deletesAll = repo.isDeleteAllActiveVersions(
                "ns5",
                "ext5",
                TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"));

        assertThat(deletesAll)
                .as("active versions of other namespaces/extensions must not inflate the 'all' count")
                .isTrue();
    }

    @Test
    void returnsFalseWhenNoTargetVersionsGiven() {
        var extension = persistExtension("ns6", "ext6");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);

        assertThat(repo.isDeleteAllActiveVersions("ns6", "ext6")).isFalse();
    }

    @Test
    void capsPreReleaseVersionsButKeepsAllStableVersions() {
        var extension = persistExtension("ns7", "ext7");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, true);
        persistVersion(extension, "2.0.0", TargetPlatform.NAME_UNIVERSAL, true);
        persistPreReleaseVersions(extension, TargetPlatform.NAME_UNIVERSAL, 1, 105);

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(List.of(extension.getId()), null, 100);

        var stable = result.stream()
                .filter(ev -> !ev.isPreRelease())
                .map(ExtensionVersion::getVersion)
                .toList();
        var preRelease = result.stream()
                .filter(ExtensionVersion::isPreRelease)
                .map(ExtensionVersion::getVersion)
                .toList();

        assertThat(stable)
                .as("stable releases must never be affected by the pre-release cap")
                .containsExactlyInAnyOrder("1.0.0", "2.0.0");
        assertThat(preRelease).as("pre-releases must be capped at 100").hasSize(100);
        assertThat(preRelease)
                .as("the highest-semver pre-release ('latest') must survive the cap")
                .contains("0.0.105");
        assertThat(preRelease)
                .as("the 5 lowest-semver pre-releases must be dropped by the cap")
                .doesNotContain("0.0.1", "0.0.2", "0.0.3", "0.0.4", "0.0.5");
    }

    @Test
    void capsPreReleaseVersionsAcrossTargetPlatformsCombinedRatherThanPerPlatform() {
        var extension = persistExtension("ns8", "ext8");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_LINUX_X64, 1, 60);
        persistPreReleaseVersions(extension, TargetPlatform.NAME_WIN32_X64, 61, 105);

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(List.of(extension.getId()), null, 100);
        var versions = result.stream().map(ExtensionVersion::getVersion).collect(Collectors.toSet());

        assertThat(result)
                .as("the cap counts pre-releases across all target platforms of the extension combined")
                .hasSize(100);
        for (var patch = 6; patch <= 105; patch++) {
            assertThat(versions).contains("0.0." + patch);
        }
        for (var patch = 1; patch <= 5; patch++) {
            assertThat(versions).doesNotContain("0.0." + patch);
        }
    }

    @Test
    void targetPlatformFilterIsAppliedAfterTheCrossPlatformPreReleaseCap() {
        // 99 higher-semver win32 pre-releases crowd out all but the newest of the 2 linux
        // pre-releases from the cross-platform top-100 cap, before the win32-x64 filter below is
        // even applied - so requesting linux-x64 only can see fewer than 100 versions even though
        // more than 100 exist for the extension overall.
        var extension = persistExtension("ns9", "ext9");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_WIN32_X64, 1, 99, "1.0.");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_LINUX_X64, 1, 2);

        var linuxOnly = repo.findAllActiveByExtensionIdAndTargetPlatform(
                List.of(extension.getId()),
                TargetPlatform.NAME_LINUX_X64,
                100);

        assertThat(linuxOnly).extracting(ExtensionVersion::getVersion).containsExactly("0.0.2");
    }

    @Test
    void honoursTheCallerSuppliedPreReleaseCapInsteadOfAFixedOne() {
        var extension = persistExtension("ns10", "ext10");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_UNIVERSAL, 1, 5);

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(List.of(extension.getId()), null, 3);

        assertThat(result)
                .as("the cap is whatever the caller passes in, not a value fixed inside the repository")
                .extracting(ExtensionVersion::getVersion)
                .containsExactlyInAnyOrder("0.0.3", "0.0.4", "0.0.5");
    }

    @Test
    void tiedMajorMinorPatchPreReleasesAreStillCappedViaTheTimestampTiebreak() {
        // All 105 rows share the exact same (major, minor, patch) - the version string itself is
        // identical - and differ only in target platform and timestamp, exactly like one release
        // published across many platform builds, or a CI channel that republishes without ever
        // bumping semver. On a (major, minor, patch)-only comparison none of these rows outranks
        // any other, so none would count against the cap and all 105 would come back uncapped.
        var extension = persistExtension("ns12", "ext12");
        var base = LocalDateTime.parse("2024-01-01T00:00:00");
        for (var i = 1; i <= 105; i++) {
            persistPreReleaseVersion(extension, "0.0.1", "platform-" + i, base.plusMinutes(i));
        }
        em.flush();

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(List.of(extension.getId()), null, 100);

        assertThat(result)
                .as("rows tied on (major, minor, patch) must still be capped, via the timestamp/id tiebreak")
                .hasSize(100);
        assertThat(result)
                .as("the most recently timestamped tied row ('latest') must survive the cap")
                .anyMatch(ev -> ev.getTargetPlatform().equals("platform-105"));
        assertThat(result)
                .as("the 5 earliest-timestamped tied rows must be dropped by the cap")
                .noneMatch(
                        ev -> List.of("platform-1", "platform-2", "platform-3", "platform-4", "platform-5")
                                .contains(ev.getTargetPlatform()));
    }

    // Regression test: the target-platform filter used to be applied via field(name("...")) against
    // whatever query shape was built, unconditionally - which happens to be valid SQL only when the
    // cap wraps the query in a CTE (maxPreReleaseVersions >= 0). With the cap disabled there is no CTE,
    // so referencing that select-list alias from the plain query's own WHERE clause is invalid SQL and
    // Postgres rejected the query outright. Since VS Code's extensionQuery always supplies a target
    // platform, this broke essentially every request under the (uncapped) default configuration.
    @Test
    void targetPlatformFilterWorksWithTheCapDisabled() {
        var extension = persistExtension("ns13", "ext13");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_LINUX_X64, true);
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_WIN32_X64, true);

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(
                List.of(extension.getId()),
                TargetPlatform.NAME_LINUX_X64,
                -1);

        assertThat(result)
                .as(
                        "the target platform filter must still apply, and must not raise a SQL error, "
                                + "when the pre-release cap is disabled")
                .extracting(ExtensionVersion::getTargetPlatform)
                .containsExactly(TargetPlatform.NAME_LINUX_X64);
    }

    @Test
    void negativeMaxPreReleaseVersionsDisablesTheCapEntirely() {
        // A negative "count < limit" can never hold, so a naive reading of the cap would exclude
        // *every* pre-release rather than none - the opposite of "unlimited". This pins down that
        // -1 (and negative values generally) instead skip the cap condition altogether, matching
        // the "negative means unlimited" convention already used elsewhere in this codebase (e.g.
        // ovsx.data.mirror.requests-per-second).
        var extension = persistExtension("ns11", "ext11");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_UNIVERSAL, 1, 105);

        var result = repo.findAllActiveByExtensionIdAndTargetPlatform(List.of(extension.getId()), null, -1);

        assertThat(result).as("a negative cap must return every active pre-release, uncapped").hasSize(105);
    }

    /**
     * The method this reworks ranked pre-releases via a correlated {@code COUNT(*)} subquery
     * ({@link #oldIsAmongLatestPreReleases}): for every pre-release row, count how many other
     * pre-releases of the <em>same extension</em> outrank it, an O(k^2) comparison for an extension
     * with k pre-releases, with no index supporting the multi-column row comparison. The rework
     * ranks with a single {@code denseRank()} window function instead, an O(k log k) sort. This pins
     * down that the rework is a wash or a win, not a regression, at the pre-release volumes this
     * method's own doc comment is written around ("one build per commit... accumulate thousands of
     * entries") - the volume the correlated subquery approach scaled worst for.
     * <p>
     * It also compares against a third baseline: {@link #noLimitFindAllActive}, a port of the method
     * as it existed before pre-release limiting existed at all (before #2062) - a bare filtered
     * SELECT with no ranking whatsoever. The uncapped path (the default, since {@code
     * ovsx.extension-query.max-pre-release-versions} defaults to {@code -1}) now skips building the
     * {@code denseRank()} window function entirely, so this pins down that it stays a wash against
     * the simplest query that could possibly answer the same question. It wasn't always a wash: an
     * earlier version of this method computed the window function unconditionally, and separately its
     * {@code RankedFieldMapper} resolved each mapped column by handing jOOQ a freshly-constructed,
     * unrelated {@code Field} object - which forces a slow fallback lookup - instead of the row's own
     * indexed {@code Field(String)} lookup. Both are fixed now; this test guards against either
     * regressing.
     * <p>
     * Both comparisons are necessarily coarse (real wall-clock time against a real Postgres instance,
     * not a controlled microbenchmark), so each allows the new query a generous margin rather than
     * asserting it is strictly faster - the point is to catch a severe regression, not to chase a
     * precise ratio.
     */
    @Test
    void newRankingApproachIsNotSlowerThanTheOldCorrelatedSubqueryApproach() {
        var preReleaseCount = 3000;
        var maxPreReleaseVersions = 100;
        var extension = persistExtension("perf-ns", "perf-ext");
        persistPreReleaseVersions(extension, TargetPlatform.NAME_UNIVERSAL, 1, preReleaseCount);
        var extensionIds = List.of(extension.getId());

        // Warm up the connection, query plan cache, and JIT with one untimed run of each shape
        // before measuring, so a one-off first-call cost doesn't skew the comparison.
        oldFindAllActive(extensionIds, maxPreReleaseVersions);
        repo.findAllActiveByExtensionIdAndTargetPlatform(extensionIds, null, maxPreReleaseVersions);
        noLimitFindAllActive(extensionIds);
        repo.findAllActiveByExtensionIdAndTargetPlatform(extensionIds, null, -1);

        var oldDuration = time(() -> oldFindAllActive(extensionIds, maxPreReleaseVersions));
        var newCappedDuration = time(
                () -> repo.findAllActiveByExtensionIdAndTargetPlatform(extensionIds, null, maxPreReleaseVersions));
        var noLimitDuration = time(() -> noLimitFindAllActive(extensionIds));
        var newUncappedDuration = time(
                () -> repo.findAllActiveByExtensionIdAndTargetPlatform(extensionIds, null, -1));

        logger.info(
                "for {} pre-release versions: old capped (correlated subquery) took {}ms, new capped (window "
                        + "function) took {}ms, no-limit-at-all baseline (pre-#2062, no ranking) took {}ms, "
                        + "new uncapped took {}ms",
                preReleaseCount,
                oldDuration.toMillis(),
                newCappedDuration.toMillis(),
                noLimitDuration.toMillis(),
                newUncappedDuration.toMillis());

        assertThat(newCappedDuration)
                .as(
                        "the reworked window-function query must not be dramatically slower than the old "
                                + "correlated-subquery query it replaces, at a pre-release volume this method's "
                                + "own doc comment is written around")
                .isLessThanOrEqualTo(oldDuration.multipliedBy(3).plus(Duration.ofMillis(500)));

        assertThat(newUncappedDuration)
                .as(
                        "the uncapped (default) path must not be a meaningful regression against the simplest "
                                + "query with no ranking capability at all - the baseline every caller got before "
                                + "pre-release limiting existed")
                .isLessThanOrEqualTo(noLimitDuration.multipliedBy(3).plus(Duration.ofMillis(500)));
    }

    private Duration time(Runnable action) {
        var start = Instant.now();
        action.run();
        return Duration.between(start, Instant.now());
    }

    /**
     * A faithful port of {@code findAllActiveByExtensionIdAndTargetPlatform} as it existed before
     * this rework (no target-platform filtering needed for this comparison), kept here only so the
     * performance test above has something to compare the reworked query against - the original
     * method itself is gone.
     */
    private int oldFindAllActive(List<Long> extensionIds, int maxPreReleaseVersions) {
        var query = dsl.select(EXTENSION_VERSION.ID)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.EXTENSION_ID.in(extensionIds));

        if (maxPreReleaseVersions >= 0) {
            query = query.and(
                    EXTENSION_VERSION.PRE_RELEASE.isFalse().or(oldIsAmongLatestPreReleases(maxPreReleaseVersions)));
        }

        return query.fetch().size();
    }

    /**
     * A faithful port of {@code findAllActiveByExtensionIdAndTargetPlatform} as it existed before
     * #2062 introduced any pre-release limiting at all: no ranking, no cap parameter, just the active
     * versions of the given extensions. Kept here only as a performance baseline - the original
     * two-argument method itself is gone.
     * <p>
     * Selects the full column list and maps every row via {@link ExtensionVersionJooqRepository#toExtensionVersion}
     * (not just an id, unlike {@link #oldFindAllActive}) so the comparison in
     * {@link #newRankingApproachIsNotSlowerThanTheOldCorrelatedSubqueryApproach} is fair: the reworked
     * method always maps every row to a full {@link ExtensionVersion}, so a baseline that skipped
     * mapping would make the SQL-only saving from dropping the unused {@code denseRank()} look far
     * smaller than it is against the dominant, unavoidable mapping cost both approaches pay equally.
     */
    private List<ExtensionVersion> noLimitFindAllActive(List<Long> extensionIds) {
        return dsl.select(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                SIGNATURE_KEY_PAIR.PUBLIC_ID)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.EXTENSION_ID.in(extensionIds))
                .fetch()
                .map(repo::toExtensionVersion);
    }

    private Condition oldIsAmongLatestPreReleases(int limit) {
        var rank = EXTENSION_VERSION.as("ev_pre_release_rank_perf");
        return DSL.field(
                dsl.selectCount()
                        .from(rank)
                        .where(rank.EXTENSION_ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                        .and(rank.ACTIVE.isTrue())
                        .and(rank.PRE_RELEASE.isTrue())
                        .and(
                                DSL.row(
                                        rank.SEMVER_MAJOR,
                                        rank.SEMVER_MINOR,
                                        rank.SEMVER_PATCH,
                                        rank.TIMESTAMP,
                                        rank.ID)
                                        .gt(
                                                DSL.row(
                                                        EXTENSION_VERSION.SEMVER_MAJOR,
                                                        EXTENSION_VERSION.SEMVER_MINOR,
                                                        EXTENSION_VERSION.SEMVER_PATCH,
                                                        EXTENSION_VERSION.TIMESTAMP,
                                                        EXTENSION_VERSION.ID))))
                .lt(limit);
    }

    private Namespace persistNamespace(String namespaceName) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        em.persist(namespace);
        return namespace;
    }

    private Extension persistExtension(String namespaceName, String extensionName) {
        return persistExtension(persistNamespace(namespaceName), extensionName);
    }

    private Extension persistExtension(Namespace namespace, String extensionName) {
        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(namespace);
        extension.setActive(true);
        extension.setDownloadable(true);
        extension.setPublishedDate(LocalDateTime.now());
        extension.setLastUpdatedDate(LocalDateTime.now());
        em.persist(extension);
        return extension;
    }

    private void persistVersion(Extension extension, String version, String targetPlatform, boolean active) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setActive(active);
        extVersion.setPublishedBy(owner);
        em.persist(extVersion);

        // ExtensionVersionJooqRepository queries run over the transaction's raw JDBC connection,
        // bypassing the persistence context, so pending inserts must be flushed before they become
        // visible to it.
        em.flush();
    }

    private void persistVersionWithTokenType(Extension extension, String version, PersonalAccessTokenType tokenType) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setActive(true);
        extVersion.setPublishedBy(owner);
        extVersion.setPublishedWithTt(tokenType);
        em.persist(extVersion);
        em.flush();
    }

    /**
     * Persists active pre-release versions {@code <versionPrefix><fromPatch>} .. {@code
     * <versionPrefix><toPatch>} (inclusive), one flush for the whole batch rather than one per row.
     */
    private void persistPreReleaseVersions(
            Extension extension,
            String targetPlatform,
            int fromPatch,
            int toPatch,
            String versionPrefix
    ) {
        for (var patch = fromPatch; patch <= toPatch; patch++) {
            var extVersion = new ExtensionVersion();
            extVersion.setExtension(extension);
            extVersion.setVersion(versionPrefix + patch);
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setActive(true);
            extVersion.setPreRelease(true);
            extVersion.setPublishedBy(owner);
            em.persist(extVersion);
        }
        em.flush();
    }

    private void persistPreReleaseVersions(Extension extension, String targetPlatform, int fromPatch, int toPatch) {
        persistPreReleaseVersions(extension, targetPlatform, fromPatch, toPatch, "0.0.");
    }

    private void persistPreReleaseVersion(
            Extension extension,
            String version,
            String targetPlatform,
            LocalDateTime timestamp
    ) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setActive(true);
        extVersion.setPreRelease(true);
        extVersion.setTimestamp(timestamp);
        extVersion.setPublishedBy(owner);
        em.persist(extVersion);
    }
}
