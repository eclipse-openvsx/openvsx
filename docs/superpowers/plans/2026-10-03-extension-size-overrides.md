# Extension Size Overrides Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce per-namespace and per-extension upload size overrides at publish time, with extension > namespace > default precedence, using the two-stage design the spec requires.

**Architecture:** A new `extension_size_override` table holds overrides scoped to a namespace (and optionally one extension). Because the namespace is not known until the VSIX has been parsed, enforcement is two-stage: the request body is streamed against a cached global **ceiling** (`max(default, highest override)`), then once `ExtensionProcessor` has parsed the manifest the **resolved** limit for that namespace/extension is compared against the already-written temp file and rejected with 413 if exceeded.

**Tech Stack:** Spring Boot (Java 25), Spring Data JPA, Flyway, Caffeine (via `localCacheManager`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md` (step 2 of "Suggested implementation order"). Phase 1 (`max-extension-size` setting + settings API/UI) is already merged — `SettingsService.getMaxExtensionSize()` exists and is the default this plan builds on.

## Global Constraints

- **Java 25**; dependencies declared in `gradle/libs.versions.toml`, never inline in `build.gradle`.
- **Nullability uses JSpecify** (`org.jspecify.annotations.Nullable`/`NonNull`), not Jakarta or Spring annotations.
- **New source files need the EPL-2.0 license header** — copy it verbatim from any existing file in the same component.
- **Flyway migrations are immutable once shipped** and live in `server/src/main/resources/db/migration` as `V<n>__Description.sql`.
- **`src/main/jooq-gen/` is generated and committed.** Regenerate with `./gradlew jooqCodegen` after a schema change — that task reads a live Postgres.
- **`./gradlew spotlessCheck` must pass.** Run `spotlessApply` and revert files you did not otherwise change.
- **Local verification command is `./gradlew unitTests`** (registered in `server/build.gradle:314-325`, excludes `@Tag("integration")`). It runs 1412 tests in ~25s with **no Docker**. `./gradlew test` additionally runs Testcontainers-backed tests and requires a working Docker daemon.
- **The server has no CHANGELOG** — put reasoning in the commit message, not a changelog file.
- Stage only files you changed (`git add <path>`), never `git add -A`.

## Decisions taken in this plan that differ from the spec

These are deliberate; do not "correct" them back to the spec text.

1. **Migration number is `V1_79`, not `V1_76`.** The spec says `V1_76__Extension_Size_Override.sql`, "latest confirmed as `V1_75`". Since the spec was written, `V1_76__Unique_Active_Review.sql`, `V1_77__Repair_Renamed_Namespace_Version_Changes.sql` and `V1_78__Published_By_Not_Null.sql` shipped. Next free is `V1_79`.
2. **No per-`(namespace, extension)` cache.** The spec (§85-88) caches the resolved limit per pair. Dropped: the resolution is a single indexed query that runs once per publish, immediately after parsing a VSIX and alongside several DB writes — caching it optimises the cheapest step of an expensive operation. It would also need its own bounded cache, because the existing `settingCache` (`cache/CacheConfig.java:145-165`) has **no `maximumSize`** and is keyed by bare strings shared with `read-only`/`max-extension-size`. Only the **ceiling** is cached.
3. **No `created_by` column at all.** The spec's DDL carries `created_by_id BIGINT NOT NULL REFERENCES user_data(id)`. Dropped: the spec's own Admin API and audit section (§100-107) already records every override mutation through `LogService.logAction` into `PersistedLog`, so a `created_by` FK stores the same fact a second time, in a place nothing reads. It also drags in a cascade decision (delete the override with the user, or block the delete?) that has no good answer. `created_at`/`updated_at` stay — they are cheap and the admin list page sorts on them.

   **Consequence for a later plan:** the spec's webui page (§122) lists a "created by/at" column. Step 4's plan must either drop the "created by" half or source it from `PersistedLog`.
4. **The table is reached through a Spring Data JPA repository, not jOOQ.** A derived query cannot express the OR-with-null scope match, and JPA keeps this plan independent of `jooqCodegen` (which needs a live Postgres). `jooqCodegen` is still required before merge — see Task 1, Step 5.
5. **Three call sites change, not one.** The spec's Enforcement section (§94) names only `ExtensionService.getMaxContentSize()`. Two more read `publishingConfig.getMaxContentSize()` directly: `LocalRegistryService.java:853` (the drain-stream cap — if it keeps the raw config value, an override *above* that value is silently truncated by the outer stream) and `LocalRegistryService.java:1409` (`/api/version`, which the CLI reads).

## File Structure

| File | Responsibility |
|---|---|
| `server/src/main/resources/db/migration/V1_79__Extension_Size_Override.sql` | Schema: sequence, table, unique scope index |
| `server/src/main/java/org/eclipse/openvsx/entities/ExtensionSizeOverride.java` | JPA entity for one override row |
| `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepository.java` | Scope lookup + `MAX(max_size)` |
| `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java` | Cached ceiling + per-publish resolution |
| `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java` | Modify: flush cache when the default changes |
| `server/src/main/java/org/eclipse/openvsx/ExtensionService.java` | Modify: stream against the ceiling |
| `server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java` | Modify: drain cap, `/api/version`, post-parse check |

---

### Task 1: Schema and entity

**Files:**
- Create: `server/src/main/resources/db/migration/V1_79__Extension_Size_Override.sql`
- Create: `server/src/main/java/org/eclipse/openvsx/entities/ExtensionSizeOverride.java`

**Interfaces:**
- Produces: table `extension_size_override`; entity `ExtensionSizeOverride` with `getId()`, `getScopeNamespace()/setScopeNamespace(Namespace)`, `getScopeExtension()/setScopeExtension(@Nullable Extension)`, `getMaxSize()/setMaxSize(long)`, `getCreatedAt()`, `getUpdatedAt()`. Tasks 2-3 consume these.
- Consumes: existing entities `Namespace`, `Extension`; `org.eclipse.openvsx.util.TimeUtil.getCurrentUTC()`.

**Testability note:** This task has no unit-testable surface — it is schema plus a mapping declaration, and any test that would exercise it needs a live Postgres (`@Tag("integration")`). Per the repo contract, that is stated explicitly here rather than skipped silently. Task 2's repository test covers the mapping.

- [ ] **Step 1: Write the migration**

Create `server/src/main/resources/db/migration/V1_79__Extension_Size_Override.sql`:

```sql
CREATE SEQUENCE IF NOT EXISTS extension_size_override_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.extension_size_override
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('extension_size_override_seq'),
    scope_namespace_id BIGINT NOT NULL REFERENCES public.namespace(id) ON DELETE CASCADE,
    scope_extension_id BIGINT REFERENCES public.extension(id) ON DELETE CASCADE,
    max_size BIGINT NOT NULL,
    created_at TIMESTAMP without time zone NOT NULL,
    updated_at TIMESTAMP without time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS extension_size_override_scope_idx
    ON public.extension_size_override (scope_namespace_id, COALESCE(scope_extension_id, 0));
```

`COALESCE(scope_extension_id, 0)` rather than `NULLS NOT DISTINCT` so the index does not depend on a specific Postgres version. It yields one namespace-wide row and one row per extension.

- [ ] **Step 2: Write the entity**

Create `server/src/main/java/org/eclipse/openvsx/entities/ExtensionSizeOverride.java`:

```java
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
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.util.TimeUtil;

@Entity
@Table(name = "extension_size_override")
public class ExtensionSizeOverride implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionSizeOverrideSeq")
    @SequenceGenerator(
            name = "extensionSizeOverrideSeq",
            sequenceName = "extension_size_override_seq",
            allocationSize = 1)
    private long id;

    @ManyToOne
    @JoinColumn(name = "scope_namespace_id", nullable = false)
    private Namespace scopeNamespace;

    /** {@code null} means the override applies to the whole namespace. */
    @ManyToOne
    @JoinColumn(name = "scope_extension_id")
    private @Nullable Extension scopeExtension;

    @Column(name = "max_size", nullable = false)
    private long maxSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = TimeUtil.getCurrentUTC();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = TimeUtil.getCurrentUTC();
    }

    public long getId() {
        return id;
    }

    public Namespace getScopeNamespace() {
        return scopeNamespace;
    }

    public void setScopeNamespace(Namespace scopeNamespace) {
        this.scopeNamespace = scopeNamespace;
    }

    public @Nullable Extension getScopeExtension() {
        return scopeExtension;
    }

    public void setScopeExtension(@Nullable Extension scopeExtension) {
        this.scopeExtension = scopeExtension;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtensionSizeOverride that)) {
            return false;
        }
        return id == that.id
                && maxSize == that.maxSize
                && Objects.equals(scopeNamespace, that.scopeNamespace)
                && Objects.equals(scopeExtension, that.scopeExtension)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scopeNamespace, scopeExtension, maxSize, createdAt, updatedAt);
    }
}
```

No registration step is needed: `RegistryApplication` is a plain `@SpringBootApplication` and package-scans `org.eclipse.openvsx`.

- [ ] **Step 3: Verify it compiles**

Run: `cd server && ./gradlew compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Check formatting**

Run: `cd server && ./gradlew spotlessCheck`

If it fails, run `./gradlew spotlessApply` and revert any file you did not create in this task.

- [ ] **Step 5: Regenerate jOOQ metadata**

Run: `cd server && ./gradlew jooqCodegen`

**This needs a running dev Postgres with the migration applied.** If no Docker daemon is available, STOP and tell your human partner — do not fake, hand-edit, or skip the generated output. `src/main/jooq-gen/` is committed, and leaving it out of sync with the schema is a defect even though nothing in this plan reads the generated classes.

Expected new files: `jooq-gen/org/eclipse/openvsx/jooq/tables/ExtensionSizeOverride.java` and `.../tables/records/ExtensionSizeOverrideRecord.java`. Expected modified: `jooq/Tables.java`, `Keys.java`, `Indexes.java`, `Sequences.java`, `Public.java`.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/resources/db/migration/V1_79__Extension_Size_Override.sql \
        server/src/main/java/org/eclipse/openvsx/entities/ExtensionSizeOverride.java \
        server/src/main/jooq-gen
git commit -m "feat: add extension_size_override table and entity"
```

---

### Task 2: Override repository

**Files:**
- Create: `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepository.java`
- Test: `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepositoryTest.java` (new, `@Tag("integration")`)

**Interfaces:**
- Produces: `ExtensionSizeOverrideRepository.findByScope(long namespaceId, @Nullable Long extensionId): List<ExtensionSizeOverride>` and `findHighestMaxSize(): @Nullable Long`. Task 3 consumes both.
- Consumes: `ExtensionSizeOverride` (Task 1).

- [ ] **Step 1: Write the repository**

Create `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepository.java`:

```java
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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.ExtensionSizeOverride;

public interface ExtensionSizeOverrideRepository extends Repository<ExtensionSizeOverride, Long> {

    // The LEFT JOIN is required: an implicit join through the nullable scopeExtension
    // association would be an INNER JOIN and would drop the namespace-wide rows.
    @Query("""
            SELECT o FROM ExtensionSizeOverride o
            LEFT JOIN o.scopeExtension e
            WHERE o.scopeNamespace.id = :namespaceId
              AND (e.id = :extensionId OR e IS NULL)
            """)
    List<ExtensionSizeOverride> findByScope(
            @Param("namespaceId") long namespaceId,
            @Param("extensionId") @Nullable Long extensionId);

    @Query("SELECT MAX(o.maxSize) FROM ExtensionSizeOverride o")
    @Nullable Long findHighestMaxSize();
}
```

The interface extends Spring Data's `org.springframework.data.repository.Repository` and needs no `@Repository` annotation, matching `TrustedPublisherRepository`.

When `:extensionId` is `null`, `e.id = :extensionId` is never true, so only the namespace-wide row matches — which is exactly the desired behaviour for an extension that has not been published yet.

- [ ] **Step 2: Write the integration test**

Create `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepositoryTest.java`, with the EPL-2.0 header, then:

```java
package org.eclipse.openvsx.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class ExtensionSizeOverrideRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    ExtensionSizeOverrideRepository overrides;

    @Test
    void findByScopeReturnsNamespaceWideRowWhenExtensionIdIsNull() {
        var namespace = persistNamespace("foo");
        persistOverride(namespace, null, 100L);

        var found = overrides.findByScope(namespace.getId(), null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getMaxSize()).isEqualTo(100L);
    }

    @Test
    void findByScopeReturnsBothNamespaceWideAndExtensionScopedRows() {
        var namespace = persistNamespace("bar");
        var extension = persistExtension(namespace, "baz");
        persistOverride(namespace, null, 100L);
        persistOverride(namespace, extension, 200L);

        var found = overrides.findByScope(namespace.getId(), extension.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ExtensionSizeOverride::getMaxSize).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void findByScopeExcludesAnotherExtensionsOverride() {
        var namespace = persistNamespace("qux");
        var wanted = persistExtension(namespace, "wanted");
        var other = persistExtension(namespace, "other");
        persistOverride(namespace, other, 200L);

        var found = overrides.findByScope(namespace.getId(), wanted.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findHighestMaxSizeReturnsNullWhenNoOverridesExist() {
        assertThat(overrides.findHighestMaxSize()).isNull();
    }

    @Test
    void findHighestMaxSizeReturnsTheLargestAcrossNamespaces() {
        var one = persistNamespace("one");
        var two = persistNamespace("two");
        persistOverride(one, null, 100L);
        persistOverride(two, null, 900L);

        assertThat(overrides.findHighestMaxSize()).isEqualTo(900L);
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        entityManager.persist(namespace);
        return namespace;
    }

    private Extension persistExtension(Namespace namespace, String name) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        entityManager.persist(extension);
        return extension;
    }

    private ExtensionSizeOverride persistOverride(Namespace namespace, Extension extension, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setScopeExtension(extension);
        override.setMaxSize(maxSize);
        entityManager.persist(override);
        entityManager.flush();
        return override;
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.settings.ExtensionSizeOverrideRepositoryTest`

Expected: PASS (5 tests). **This needs Docker.** If no Docker daemon is available, say so explicitly and move on to Task 3 — Task 3's tests do not need it. Do not delete or weaken this test to make it run.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepository.java \
        server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeOverrideRepositoryTest.java
git commit -m "feat: add repository for extension size override scope lookups"
```

---

### Task 3: Ceiling and resolution service

**Files:**
- Create: `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java` (the `updateFromJson` method)
- Test: `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java` (new)

**Interfaces:**
- Produces: `ExtensionSizeLimitService.getCeiling(): long`, `getDefaultLimit(): long`, `resolveLimit(String namespaceName, String extensionName): long`. Task 4 consumes all three.
- Consumes: `ExtensionSizeOverrideRepository` (Task 2); `SettingsService.getMaxExtensionSize()` and `SettingsCache.clear()` (both already exist from Phase 1); `RepositoryService.findNamespace(String)` and `findExtension(String name, Namespace namespace)`; `CacheService.CACHE_SETTING`.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java`, with the EPL-2.0 header, then:

```java
package org.eclipse.openvsx.settings;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ExtensionSizeLimitServiceTest {

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
        when(settings.getMaxExtensionSize()).thenReturn(DEFAULT_LIMIT);
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

    private ExtensionSizeOverride override(Extension scopeExtension, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeExtension(scopeExtension);
        override.setMaxSize(maxSize);
        return override;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.settings.ExtensionSizeLimitServiceTest`

Expected: compile failure — `ExtensionSizeLimitService` does not exist.

- [ ] **Step 3: Write the service**

Create `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java`, with the EPL-2.0 header, then:

```java
package org.eclipse.openvsx.settings;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.repositories.RepositoryService;

@Service
@CacheConfig(cacheManager = "localCacheManager")
public class ExtensionSizeLimitService {

    private static final String CACHE_KEY_CEILING = "'extension-size-ceiling'";

    private final SettingsService settings;
    private final ExtensionSizeOverrideRepository overrides;
    private final RepositoryService repositories;

    public ExtensionSizeLimitService(
            SettingsService settings,
            ExtensionSizeOverrideRepository overrides,
            RepositoryService repositories
    ) {
        this.settings = settings;
        this.overrides = overrides;
        this.repositories = repositories;
    }

    public long getDefaultLimit() {
        return settings.getMaxExtensionSize();
    }

    /**
     * Largest package any namespace may publish. Used as the stream-time cap, before the namespace
     * is known. Evicted by {@link SettingsCache#clear()}, which every settings write triggers.
     */
    @Cacheable(value = CacheService.CACHE_SETTING, key = CACHE_KEY_CEILING)
    public long getCeiling() {
        var defaultLimit = getDefaultLimit();
        var highest = overrides.findHighestMaxSize();
        return highest == null ? defaultLimit : Math.max(defaultLimit, highest);
    }

    /** Applicable limit for one package: extension override, else namespace override, else default. */
    public long resolveLimit(String namespaceName, String extensionName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            return getDefaultLimit();
        }

        var extension = repositories.findExtension(extensionName, namespace);
        var extensionId = extension == null ? null : extension.getId();

        long namespaceWide = -1;
        for (var override : overrides.findByScope(namespace.getId(), extensionId)) {
            if (override.getScopeExtension() != null) {
                return override.getMaxSize();
            }
            namespaceWide = override.getMaxSize();
        }
        return namespaceWide >= 0 ? namespaceWide : getDefaultLimit();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.settings.ExtensionSizeLimitServiceTest`

Expected: PASS (7 tests).

- [ ] **Step 5: Flush the cached ceiling when the default changes**

The ceiling is cached under its own key, so changing `max-extension-size` would otherwise leave it stale for up to the cache TTL on a deployment with no Redis (where `publishSettingsUpdate()` is a no-op). In `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java`, in `updateFromJson`, replace:

```java
        if (newSettings.getMaxExtensionSize() != getMaxExtensionSize()) {
            changes.add("maxExtensionSize -> " + newSettings.getMaxExtensionSize());
            cache.setLong(SETTING_MAX_EXTENSION_SIZE, newSettings.getMaxExtensionSize());
        }
```

with:

```java
        if (newSettings.getMaxExtensionSize() != getMaxExtensionSize()) {
            changes.add("maxExtensionSize -> " + newSettings.getMaxExtensionSize());
            cache.setLong(SETTING_MAX_EXTENSION_SIZE, newSettings.getMaxExtensionSize());
            // The derived size ceiling is cached under its own key, so evict the whole settings
            // cache rather than just this one entry.
            cache.clear();
        }
```

- [ ] **Step 6: Cover the flush with a test**

In `server/src/test/java/org/eclipse/openvsx/settings/SettingsServiceTest.java`, add this test method:

```java
    @Test
    void updateFromJsonFlushesTheCacheWhenMaxExtensionSizeChanges() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(1024L * 1024 * 1024);

        settings.updateFromJson(newSettings);

        verify(cache).clear();
    }

    @Test
    void updateFromJsonDoesNotFlushTheCacheWhenMaxExtensionSizeIsUnchanged() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(512L * 1024 * 1024);

        settings.updateFromJson(newSettings);

        verify(cache, Mockito.never()).clear();
    }
```

- [ ] **Step 7: Run both test classes**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.settings.ExtensionSizeLimitServiceTest --tests org.eclipse.openvsx.settings.SettingsServiceTest`

Expected: PASS (7 + 7 tests).

- [ ] **Step 8: Format and commit**

Run: `cd server && ./gradlew spotlessCheck` (run `spotlessApply` first if it fails, then revert unrelated files).

```bash
git add server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java \
        server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java \
        server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java \
        server/src/test/java/org/eclipse/openvsx/settings/SettingsServiceTest.java
git commit -m "feat: resolve extension size limits from namespace and extension overrides"
```

---

### Task 4: Two-stage enforcement in the publish path

**Files:**
- Modify: `server/src/main/java/org/eclipse/openvsx/ExtensionService.java:64-101` (field, constructor, `getMaxContentSize`)
- Modify: `server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java:853`, `:858-873`, `:1409`
- Test: `server/src/test/java/org/eclipse/openvsx/ExtensionSizeEnforcementTest.java` (new)

**Interfaces:**
- Consumes: `ExtensionSizeLimitService.getCeiling()/getDefaultLimit()/resolveLimit(String, String)` (Task 3); `ExtensionProcessor.getNamespace()/getExtensionName()`; `NamingUtil.toExtensionId(String, String)`; `ErrorResultException(String, HttpStatusCode)`.
- Produces: no new API. Behaviour change only.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/org/eclipse/openvsx/ExtensionSizeEnforcementTest.java`, with the EPL-2.0 header, then:

```java
package org.eclipse.openvsx;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ExtensionSizeEnforcementTest {

    @Test
    void createExtensionFileAcceptsAPackageUpToTheCeiling() throws Exception {
        var limits = Mockito.mock(ExtensionSizeLimitService.class);
        when(limits.getCeiling()).thenReturn(10L);
        var service = newExtensionService(limits);

        try (var file = service.createExtensionFile(new ByteArrayInputStream(new byte[10]))) {
            assertThat(Files.size(file.getPath())).isEqualTo(10L);
        }
    }

    @Test
    void createExtensionFileRejectsAPackageAboveTheCeilingWith413() {
        var limits = Mockito.mock(ExtensionSizeLimitService.class);
        when(limits.getCeiling()).thenReturn(10L);
        var service = newExtensionService(limits);

        assertThatThrownBy(() -> service.createExtensionFile(new ByteArrayInputStream(new byte[11])))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("size limit")
                .extracting(e -> ((ErrorResultException) e).getStatus())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    private ExtensionService newExtensionService(ExtensionSizeLimitService limits) {
        return new ExtensionService(
                limits,
                Mockito.mock(jakarta.persistence.EntityManager.class),
                Mockito.mock(org.eclipse.openvsx.repositories.RepositoryService.class),
                Mockito.mock(org.eclipse.openvsx.search.SearchUtilService.class),
                Mockito.mock(org.eclipse.openvsx.cache.CacheService.class),
                Mockito.mock(org.eclipse.openvsx.util.LogService.class),
                Mockito.mock(org.eclipse.openvsx.publish.PublishExtensionVersionHandler.class),
                Mockito.mock(org.jobrunr.scheduling.JobRequestScheduler.class),
                Mockito.mock(org.eclipse.openvsx.scanning.ExtensionScanService.class),
                Mockito.mock(org.eclipse.openvsx.scanning.ExtensionScanPersistenceService.class));
    }
}
```

`ErrorResultException.getStatus()` returns `HttpStatusCode` (`ErrorResultException.java:55`), so the assertion above compiles as written.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.ExtensionSizeEnforcementTest`

Expected: compile failure — `ExtensionService`'s constructor still takes `PublishingConfig` as its first parameter.

- [ ] **Step 3: Stream against the ceiling in `ExtensionService`**

In `server/src/main/java/org/eclipse/openvsx/ExtensionService.java`, replace the `publishingConfig` field declaration (line 64):

```java
    private final PublishingConfig publishingConfig;
```

with:

```java
    private final ExtensionSizeLimitService sizeLimits;
```

Replace the first constructor parameter and its assignment:

```java
            PublishingConfig publishingConfig,
```
becomes
```java
            ExtensionSizeLimitService sizeLimits,
```

and

```java
        this.publishingConfig = publishingConfig;
```
becomes
```java
        this.sizeLimits = sizeLimits;
```

Replace `getMaxContentSize` (lines 99-101):

```java
    private long getMaxContentSize() {
        return publishingConfig.getMaxContentSize();
    }
```

with:

```java
    private long getMaxContentSize() {
        return sizeLimits.getCeiling();
    }
```

Replace the import `import org.eclipse.openvsx.publish.PublishingConfig;` with `import org.eclipse.openvsx.settings.ExtensionSizeLimitService;`, keeping the existing import ordering.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd server && ./gradlew test --tests org.eclipse.openvsx.ExtensionSizeEnforcementTest`

Expected: PASS (2 tests).

- [ ] **Step 5: Raise the drain cap and the advertised limit in `LocalRegistryService`**

Add a field and constructor parameter for `ExtensionSizeLimitService sizeLimits` alongside the existing `publishingConfig` field (`LocalRegistryService.java:99`), following the surrounding style. Keep `publishingConfig` — other code may still use it; remove it only if the compiler reports it unused.

At line 853, replace:

```java
        try (var content = new DrainOnCloseInputStream(rawContent, publishingConfig.getMaxContentSize())) {
```

with:

```java
        try (var content = new DrainOnCloseInputStream(rawContent, sizeLimits.getCeiling())) {
```

At line 1409, replace:

```java
        json.setMaxExtensionSize(publishingConfig.getMaxContentSize());
```

with:

```java
        json.setMaxExtensionSize(sizeLimits.getDefaultLimit());
```

`/api/version` has no namespace context, so it advertises the global default — the value the CLI uses for its advisory check.

- [ ] **Step 6: Add the post-parse check**

In `doPublish`, inside the `try (var processor = new ExtensionProcessor(tempFile))` block, after the publisher-agreement check and immediately before `extVersion = extensions.publishVersion(processor, au);`, insert:

```java
                    var limit = sizeLimits.resolveLimit(processor.getNamespace(), processor.getExtensionName());
                    long actualSize;
                    try {
                        actualSize = Files.size(tempFile.getPath());
                    } catch (IOException e) {
                        throw new ErrorResultException("Failed to read extension file", e);
                    }
                    if (actualSize > limit) {
                        throw new ErrorResultException(
                                "The extension package exceeds the size limit of "
                                        + FileUtils.byteCountToDisplaySize(limit) + " for "
                                        + NamingUtil.toExtensionId(
                                                processor.getNamespace(), processor.getExtensionName())
                                        + ". See " + SIZE_LIMIT_DOCS_URL + " for details.",
                                HttpStatus.CONTENT_TOO_LARGE);
                    }
```

`Files.size`'s `IOException` is rewrapped as an `ErrorResultException` deliberately: letting it reach the outer `catch (IOException)` would bypass the `catch (RuntimeException)` block that calls `IOUtils.closeQuietly(tempFile)` and would leak the temp file.

Add the constant next to the other constants at the top of the class:

```java
    private static final String SIZE_LIMIT_DOCS_URL =
            "https://github.com/eclipse-openvsx/openvsx/wiki/Publishing-Extensions#size-limits";
```

**Dependency:** step 6 of the spec's implementation order owns the public documentation. Confirm this anchor exists, or agree the real URL with your human partner, before this ships — do not leave a link that 404s.

Add any missing imports: `java.io.IOException`, `java.nio.file.Files`, `org.apache.commons.io.FileUtils`, `org.eclipse.openvsx.util.NamingUtil`, `org.eclipse.openvsx.settings.ExtensionSizeLimitService`.

- [ ] **Step 7: Fix the remaining construction sites**

Run: `cd server && ./gradlew compileJava compileTestJava`

`ExtensionService`'s constructor changed, so every direct `new ExtensionService(...)` and every Spring test that mocks `PublishingConfig` for it needs updating. Fix each compile error by passing an `ExtensionSizeLimitService` mock. Do not add a second constructor to avoid this.

- [ ] **Step 8: Run the full unit tier**

Run: `cd server && ./gradlew unitTests`

Expected: PASS, with the count at least the 1412 that passed before this plan plus the tests added here. Investigate any regression rather than adjusting the assertion.

- [ ] **Step 9: Format and commit**

Run: `cd server && ./gradlew spotlessCheck` (run `spotlessApply` first if it fails, then revert unrelated files).

```bash
git add server/src/main/java/org/eclipse/openvsx/ExtensionService.java \
        server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java \
        server/src/test/java/org/eclipse/openvsx/ExtensionSizeEnforcementTest.java
git commit -m "feat: enforce per-namespace extension size overrides when publishing"
```

---

## Self-Review Notes

- **Spec coverage.** Step 2 of the spec's implementation order has five parts: entity + migration (Task 1), resolution query (Task 2), ceiling caching (Task 3), two-stage enforcement (Task 4), and the 413 message with the applicable limit and a docs link (Task 4, Step 6). Per-scope caching is deliberately dropped — see "Decisions taken in this plan that differ from the spec". The hard ceiling (`ovsx.publishing.max-override-size`), `AdminAPI` CRUD and audit logging belong to step 3 and are **not** in this plan, so no new configuration property is introduced and the four deployment descriptors listed in `server/CLAUDE.md` need no changes.
- **What cannot be verified without Docker.** Task 1 Step 5 (`jooqCodegen`) and Task 2 Step 3 (the repository integration test). Everything else runs under `./gradlew unitTests`. Both are called out in place with an instruction to stop and report rather than fake the result.
- **Type consistency.** `findByScope(long, Long)` and `findHighestMaxSize()` are defined in Task 2 and used with those exact names and types in Task 3. `getCeiling()`, `getDefaultLimit()` and `resolveLimit(String, String)` are defined in Task 3 and used with those exact names in Task 4.
- **Known residual risk.** The post-parse check runs after `useAccessToken` and `checkPublisherAgreement`, matching the spec's placement (§95). An oversized upload from a namespace with no override is therefore accepted up to the ceiling before rejection — the trade-off the spec states plainly (§30) and accepts.
- **Behaviour when nothing is configured.** With no override rows, `findHighestMaxSize()` returns `null`, the ceiling equals `SettingsService.getMaxExtensionSize()`, and `resolveLimit` returns the same value for every package — so a deployment that never creates an override behaves exactly as it does after Phase 1.
