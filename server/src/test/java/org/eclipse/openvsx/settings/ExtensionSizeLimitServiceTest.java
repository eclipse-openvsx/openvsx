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
package org.eclipse.openvsx.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionSizeLimitServiceTest {

    private static final String INDEX_NAME = "extension_size_override_scope_idx";

    private static final long DEFAULT_LIMIT = 512L * 1024 * 1024;

    private SettingsService settings;
    private ExtensionSizeOverrideRepository overrides;
    private RepositoryService repositories;
    private ExtensionSizeLimitService limits;

    @BeforeEach
    void setUp() {
        settings = Mockito.mock(SettingsService.class);
        overrides = Mockito.mock(ExtensionSizeOverrideRepository.class);
        repositories = Mockito.mock(RepositoryService.class);
        // lenient: the write tests below never read the default limit.
        Mockito.lenient().when(settings.getMaxExtensionSize()).thenReturn(DEFAULT_LIMIT);
        limits = new ExtensionSizeLimitService(settings, overrides, repositories);
    }

    @Test
    void ceilingIsTheDefaultWhenNoOverridesExist() {
        when(overrides.findHighestMaxSize()).thenReturn(null);

        assertThat(limits.getCeiling()).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void ceilingRisesToTheHighestOverride() {
        when(overrides.findHighestMaxSize()).thenReturn(2L * DEFAULT_LIMIT);

        assertThat(limits.getCeiling()).isEqualTo(2L * DEFAULT_LIMIT);
    }

    @Test
    void ceilingStaysAtTheDefaultWhenEveryOverrideIsSmaller() {
        when(overrides.findHighestMaxSize()).thenReturn(1024L);

        assertThat(limits.getCeiling()).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void resolveFallsBackToTheDefaultForAnUnknownNamespace() {
        when(repositories.findNamespace("nope")).thenReturn(null);

        assertThat(limits.resolveLimit("nope", "ext")).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void resolveUsesTheNamespaceWideOverrideWhenNoExtensionOverrideExists() {
        var namespace = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(null);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of(override(null, 100L)));

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(100L);
    }

    @Test
    void resolvePrefersTheExtensionOverrideOverTheNamespaceOne() {
        var namespace = namespace("foo", 1L);
        var extension = extension(7L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(extension);
        when(overrides.findByScope(eq(1L), eq(7L)))
                .thenReturn(List.of(override(null, 100L), override(extension, 300L)));

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(300L);
    }

    @Test
    void resolveFallsBackToTheDefaultWhenTheNamespaceHasNoOverrides() {
        var namespace = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(null);
        when(overrides.findByScope(anyLong(), eq(null))).thenReturn(List.of());

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void createRejectsAnUnknownNamespace() {
        when(repositories.findNamespace("nope")).thenReturn(null);

        assertThatThrownBy(() -> limits.createOverride("nope", null, 100L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown namespace");
    }

    @Test
    void createRejectsANonPositiveSize() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(true);

        assertThatThrownBy(() -> limits.createOverride("foo", null, 0L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void createAcceptsASizeAboveTheFormerHardCeiling() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(true);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of());
        when(overrides.save(any(ExtensionSizeOverride.class))).thenAnswer(i -> i.getArgument(0));

        var created = limits.createOverride("foo", null, Long.MAX_VALUE);

        assertThat(created.getMaxSize()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void createRejectsADuplicateScope() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(true);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of(override(null, 100L)));

        assertThatThrownBy(() -> limits.createOverride("foo", null, 200L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createRejectsAnExtensionThatIsNotInTheNamespace() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(true);
        when(repositories.findExtension(eq("ghost"), any(Namespace.class))).thenReturn(null);

        assertThatThrownBy(() -> limits.createOverride("foo", "ghost", 200L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown extension");
    }

    /**
     * Overrides are only granted to verified namespaces. An existing override deliberately survives
     * verification lapsing, so this is checked on creation only.
     */
    @Test
    void createRejectsAnUnverifiedNamespace() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(false);

        assertThatThrownBy(() -> limits.createOverride("foo", null, 100L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void createSavesTheOverrideAndInvalidatesTheCeiling() {
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.isVerified(ns)).thenReturn(true);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of());
        when(overrides.save(any(ExtensionSizeOverride.class))).thenAnswer(i -> i.getArgument(0));

        var created = limits.createOverride("foo", null, 200L);

        assertThat(created.getMaxSize()).isEqualTo(200L);
        assertThat(created.getScopeNamespace()).isSameAs(ns);
        assertThat(created.getScopeExtension()).isNull();
        verify(overrides).save(any(ExtensionSizeOverride.class));
        verify(settings).invalidateCache();
    }

    @Test
    void updateRejectsAnUnknownId() {
        when(overrides.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> limits.updateOverride(42L, 200L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown size override");
    }

    /**
     * No {@code save}: the row is loaded and written in one transaction, so the entity stays managed
     * and the setter is what persists it. Saving a detached copy back was a merge that rewrote every
     * column from a stale snapshot.
     */
    @Test
    void updateChangesTheSizeAndInvalidatesTheCeiling() {
        when(overrides.findById(42L)).thenReturn(Optional.of(override(null, 100L)));

        var updated = limits.updateOverride(42L, 200L);

        assertThat(updated.override().getMaxSize()).isEqualTo(200L);
        assertThat(updated.previousMaxSize()).isEqualTo(100L);
        verify(overrides, Mockito.never()).save(any(ExtensionSizeOverride.class));
        verify(settings).invalidateCache();
    }

    @Test
    void deleteRemovesTheOverrideAndInvalidatesTheCeiling() {
        var existing = override(null, 100L);
        when(overrides.findById(42L)).thenReturn(Optional.of(existing));

        var deleted = limits.deleteOverride(42L);

        assertThat(deleted).isSameAs(existing);
        verify(overrides).delete(existing);
        verify(settings).invalidateCache();
    }

    /**
     * The namespace change moves every extension without touching this table, so an extension-scoped
     * override left behind has scope ids that disagree and resolveLimit stops finding it - which reads
     * exactly like no override at all.
     */
    @Test
    void moveCarriesAnExtensionOverrideToTheNewNamespace() {
        var oldNs = namespace("old", 1L);
        var newNs = namespace("new", 2L);
        var override = override(extension(7L), 100L);
        when(overrides.findByScopeNamespace(oldNs)).thenReturn(List.of(override));
        when(overrides.findByScopeNamespace(newNs)).thenReturn(List.of());

        limits.moveOverridesToNamespace(oldNs, newNs, true);

        assertThat(override.getScopeNamespace()).isSameAs(newNs);
        verify(settings).invalidateCache();
    }

    @Test
    void moveCarriesTheNamespaceWideOverrideWhenTheOldNamespaceIsGoingAway() {
        var oldNs = namespace("old", 1L);
        var newNs = namespace("new", 2L);
        var override = override(null, 100L);
        when(overrides.findByScopeNamespace(oldNs)).thenReturn(List.of(override));
        when(overrides.findByScopeNamespace(newNs)).thenReturn(List.of());

        limits.moveOverridesToNamespace(oldNs, newNs, true);

        assertThat(override.getScopeNamespace()).isSameAs(newNs);
    }

    @Test
    void moveLeavesTheNewNamespacesOwnLimitInPlace() {
        var oldNs = namespace("old", 1L);
        var newNs = namespace("new", 2L);
        var fromOld = override(null, 100L);
        when(overrides.findByScopeNamespace(oldNs)).thenReturn(List.of(fromOld));
        when(overrides.findByScopeNamespace(newNs)).thenReturn(List.of(override(null, 900L)));

        limits.moveOverridesToNamespace(oldNs, newNs, true);

        assertThat(fromOld.getScopeNamespace()).isNotSameAs(newNs);
    }

    @Test
    void moveDoesNothingWhenTheOldNamespaceHasNoOverrides() {
        var oldNs = namespace("old", 1L);
        when(overrides.findByScopeNamespace(oldNs)).thenReturn(List.of());

        limits.moveOverridesToNamespace(oldNs, namespace("new", 2L), true);

        verify(settings, Mockito.never()).invalidateCache();
    }

    /**
     * The predicate matches on the index name as a string, so a rename on either side without the
     * other would silently stop the retry and turn a concurrent create back into a 500. The name is
     * taken from the migration and fed to the real predicate rather than repeated here, so a typo in
     * either place fails this.
     */
    @Test
    void theRetryPredicateMatchesTheIndexTheMigrationCreates() throws IOException {
        var migration = Files.readString(
                Path.of("src/main/resources/db/migration/V1_79__Extension_Size_Override.sql"));
        var declared = Pattern.compile("CREATE UNIQUE INDEX IF NOT EXISTS (\\w+)").matcher(migration);
        assertThat(declared.find()).isTrue();
        var indexName = declared.group(1);

        var violation = new ConstraintViolationException("insert failed", new SQLException(), indexName);

        assertThat(
                new ExtensionSizeLimitService.DuplicateScopePredicate()
                        .shouldRetry(null, new DataIntegrityViolationException("wrapped", violation)))
                .isTrue();
    }

    @Test
    void retriesOnlyTheDuplicateScopeViolation() {
        var predicate = new ExtensionSizeLimitService.DuplicateScopePredicate();
        var duplicate = new ConstraintViolationException("insert failed", new SQLException(), INDEX_NAME);
        var unrelated = new ConstraintViolationException("insert failed", new SQLException(), "some_other_idx");

        assertThat(predicate.shouldRetry(null, new DataIntegrityViolationException("wrapped", duplicate))).isTrue();
        assertThat(predicate.shouldRetry(null, new DataIntegrityViolationException("wrapped", unrelated))).isFalse();
        assertThat(predicate.shouldRetry(null, new IllegalStateException("nothing to do with it"))).isFalse();
    }

    /** Postgres does not always report the constraint name, so the message is the fallback. */
    @Test
    void retriesWhenOnlyTheMessageNamesTheIndex() {
        var predicate = new ExtensionSizeLimitService.DuplicateScopePredicate();
        var reported = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"" + INDEX_NAME + "\"");

        assertThat(predicate.shouldRetry(null, reported)).isTrue();
    }

    private Namespace namespace(String name, long id) {
        var namespace = Mockito.mock(Namespace.class);
        when(namespace.getId()).thenReturn(id);
        when(namespace.getName()).thenReturn(name);
        return namespace;
    }

    private Extension extension(long id) {
        var extension = Mockito.mock(Extension.class);
        when(extension.getId()).thenReturn(id);
        return extension;
    }

    private ExtensionSizeOverride override(@Nullable Extension scopeExtension, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeExtension(scopeExtension);
        override.setMaxSize(maxSize);
        return override;
    }
}
