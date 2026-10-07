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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.DisplayNameUtil;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExtensionJooqRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    ExtensionJooqRepository repo;

    @Autowired
    EntityManager em;

    @Autowired
    DSLContext dsl;

    /**
     * TOB-OVSX-37: {@code findFirstUnresolvedDependency} joined the extension table by name only, not
     * constrained to the namespace resolved by the preceding join. A dependency naming an existing
     * namespace that does not contain the named extension, where that extension name exists under a
     * different namespace, was therefore incorrectly reported as resolved.
     */
    @Test
    void reportsDependencyAsUnresolvedWhenExtensionNameExistsOnlyInAnotherNamespace() {
        persistExtension("trustednamespace", "unrelated-extension");
        persistExtension("otherns", "popular-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("trustednamespace.popular-extension");
    }

    @Test
    void reportsDependencyAsResolvedWhenNamespaceAndExtensionMatch() {
        persistExtension("trustednamespace", "popular-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isNull();
    }

    @Test
    void reportsDependencyAsUnresolvedWhenNamespaceDoesNotExist() {
        var dependency = new ExtensionId("unknownnamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("unknownnamespace.popular-extension");
    }

    @Test
    void reportsDependencyAsUnresolvedWhenExtensionDoesNotExistInAnExistingNamespace() {
        persistExtension("trustednamespace", "unrelated-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("trustednamespace.popular-extension");
    }

    @Test
    void matchesNamespaceAndExtensionNameIgnoringCase() {
        persistExtension("trustednamespace", "popular-extension");

        var dependency = new ExtensionId("TrustedNamespace", "Popular-Extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isNull();
    }

    @Test
    void findsTheExtensionShowingTheGivenDisplayName() {
        var squatted = persistExtension("dn-original-ns", "original-extension");
        persistVersion(squatted, "1.0.0", "Pretty Formatter", true);

        var conflict = repo.findActiveExtensionByDisplayName("Pretty Formatter", List.of("dn-publisher-ns"));

        assertThat(conflict).isNotNull();
        assertThat(conflict.getName()).isEqualTo("original-extension");
        assertThat(conflict.getNamespace().getName()).isEqualTo("dn-original-ns");
    }

    @Test
    void matchesDisplayNamesDifferingOnlyInCaseOrSurroundingWhitespace() {
        // Neither is visible when the two names are read side by side, so neither is enough to tell
        // the extensions apart -- which is exactly what an impersonation would rely on.
        var squatted = persistExtension("dn-case-ns", "original-extension");
        persistVersion(squatted, "1.0.0", "Pretty Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("pretty formatter", List.of())).isNotNull();
        assertThat(repo.findActiveExtensionByDisplayName("PRETTY FORMATTER", List.of())).isNotNull();
        assertThat(repo.findActiveExtensionByDisplayName("  Pretty Formatter  ", List.of())).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("spaceSeparators")
    void matchesDisplayNamesPaddedWithAnySpaceSeparator(int separator) {
        var space = Character.toString(separator);
        var padded = persistExtension("dn-padded-ns", "padded-extension");
        persistVersion(padded, "1.0.0", space + "Padded Formatter" + space, true);
        var plain = persistExtension("dn-plain-ns", "plain-extension");
        persistVersion(plain, "1.0.0", "Plain Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Padded Formatter", List.of())).isNotNull();
        assertThat(repo.findActiveExtensionByDisplayName(space + "plain formatter" + space, List.of())).isNotNull();
    }

    @Test
    void matchesDisplayNamesPaddedWithMixedSpaceSeparators() {
        var extension = persistExtension("dn-mixed-ns", "mixed-extension");
        persistVersion(extension, "1.0.0", "\u2007\u00A0Pretty Formatter\u202F", true);

        assertThat(repo.findActiveExtensionByDisplayName("\u3000 Pretty Formatter\u205F\u1680", List.of()))
                .isNotNull();
    }

    @Test
    void doesNotMatchADisplayNameDifferingInInteriorWhitespace() {
        var extension = persistExtension("dn-interior-ns", "interior-extension");
        persistVersion(extension, "1.0.0", "Pretty Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Pretty\u00A0Formatter", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Pretty  Formatter", List.of())).isNull();
    }

    @Test
    void reportsNoConflictForADisplayNameOfOnlySpaceSeparators() {
        // Normalised to nothing, it would otherwise match every extension showing a blank name.
        var blank = persistExtension("dn-blank-ns", "blank-extension");
        persistVersion(blank, "1.0.0", "\u202F", true);

        assertThat(repo.findActiveExtensionByDisplayName("\u00A0", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("\u00A0\u2007", List.of())).isNull();
    }

    @Test
    void displayNameLookupUsesTheDisplayNameIndexUnderAGenericPlan() {
        // A generic plan keeps bind parameters unresolved, so it only uses the index when the query's
        // expression is identical to the one V1_76 indexes.
        for (var i = 0; i < 50; i++) {
            persistVersion(persistExtension("dn-seed-ns-" + i, "seed-extension"), "1.0.0", "Seed Formatter " + i, true);
        }
        // On empty, unanalysed tables the planner picks indexes by tie-break rather than selectivity.
        dsl.execute("ANALYZE namespace, extension, extension_version");

        var query = repo.findActiveExtensionByDisplayNameQuery("Pretty Formatter", List.of("dn-publisher-ns"));
        var sql = DSL.using(dsl.dialect(), new Settings().withRenderNamedParamPrefix("$")).renderNamedParams(query);
        var arguments = query.getBindValues().stream()
                .map(value -> dsl.renderInlined(DSL.inline(value)))
                .collect(Collectors.joining(", "));

        dsl.execute("SET LOCAL plan_cache_mode = force_generic_plan");
        dsl.execute("SET LOCAL enable_seqscan = off");
        dsl.execute("PREPARE display_name_lookup AS " + sql);
        try {
            var plan = dsl.fetch("EXPLAIN EXECUTE display_name_lookup(" + arguments + ")")
                    .getValues(0, String.class);
            assertThat(String.join("\n", plan)).contains("extension_version_display_name_idx");
        } finally {
            dsl.execute("DEALLOCATE display_name_lookup");
        }
    }

    @Test
    void reportsNoConflictForADisplayNameNobodyShows() {
        var extension = persistExtension("dn-unused-ns", "original-extension");
        persistVersion(extension, "1.0.0", "Pretty Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Prettier Formatter", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Pretty", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName(null, List.of())).isNull();
    }

    @Test
    void skipsTheExcludedNamespaces() {
        var extension = persistExtension("dn-excluded-ns", "original-extension");
        persistVersion(extension, "1.0.0", "Pretty Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Pretty Formatter", List.of("dn-excluded-ns"))).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Pretty Formatter", List.of("DN-Excluded-NS"))).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Pretty Formatter", List.of("dn-unrelated-ns"))).isNotNull();
    }

    @Test
    void ignoresExtensionsAndVersionsThatAreNotPubliclyVisible() {
        // A name nobody can see is a name nobody can be misled by, and rejecting a publication over one
        // would hand out reservations on display names that no extension actually shows.
        var inactiveVersion = persistExtension("dn-inactive-version-ns", "inactive-version-extension");
        persistVersion(inactiveVersion, "1.0.0", "Hidden Formatter", false);

        var inactiveExtension = persistExtension("dn-inactive-ext-ns", "inactive-extension", false);
        persistVersion(inactiveExtension, "1.0.0", "Withdrawn Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Hidden Formatter", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Withdrawn Formatter", List.of())).isNull();
    }

    @Test
    void onlyConsidersTheDisplayNameOfTheLatestVersion() {
        // The latest version is the one the registry shows, so a name an extension has moved away from
        // is no longer taken, while the name it moved to is.
        var renamed = persistExtension("dn-renamed-ns", "renamed-extension");
        persistVersion(renamed, "1.0.0", "Former Formatter", true);
        persistVersion(renamed, "2.0.0", "Current Formatter", true);

        assertThat(repo.findActiveExtensionByDisplayName("Former Formatter", List.of())).isNull();
        assertThat(repo.findActiveExtensionByDisplayName("Current Formatter", List.of())).isNotNull();
    }

    static IntStream spaceSeparators() {
        return DisplayNameUtil.SURROUNDING_WHITESPACE.chars();
    }

    private Extension persistExtension(String namespaceName, String extensionName) {
        return persistExtension(namespaceName, extensionName, true);
    }

    private Extension persistExtension(String namespaceName, String extensionName, boolean active) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        em.persist(namespace);

        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(namespace);
        extension.setActive(active);
        extension.setDeprecated(false);
        extension.setDownloadable(true);
        extension.setPublishedDate(LocalDateTime.now());
        extension.setLastUpdatedDate(LocalDateTime.now());
        em.persist(extension);

        // ExtensionJooqRepository queries run over the transaction's raw JDBC connection, bypassing
        // the persistence context, so pending inserts must be flushed before they become visible to it.
        em.flush();
        return extension;
    }

    private void persistVersion(Extension extension, String version, String displayName, boolean active) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setDisplayName(displayName);
        extVersion.setActive(active);
        extVersion.setTimestamp(LocalDateTime.now());
        em.persist(extVersion);
        em.flush();
    }
}
