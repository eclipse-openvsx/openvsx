# Extension Size Override Admin API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let admins create, update and delete per-namespace and per-extension size overrides through an HTTP API, audited into `PersistedLog` and bounded by a configurable hard ceiling.

**Architecture:** A new focused controller `ExtensionSizeOverrideAPI` (modelled on `RateLimitAPI`, not bolted onto the already 1552-line `AdminAPI`) exposes list/create/update/delete. Write logic lives on the existing `ExtensionSizeLimitService`, which already owns this domain — it validates against the new `ovsx.publishing.max-override-size` ceiling and, critically, invalidates the cached stream-time ceiling after every mutation.

**Tech Stack:** Spring Boot (Java 25), Spring Data JPA, JUnit 5 + Mockito + AssertJ, MockMvc (`@WebMvcTest`).

**Spec:** `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md` (step 3 of "Suggested implementation order"). Steps 1 and 2 are merged: `SettingsService.getMaxExtensionSize()`, the `extension_size_override` table, `ExtensionSizeLimitService.getCeiling()/resolveLimit()` and two-stage publish enforcement all exist.

## Global Constraints

- **Java 25**; dependencies declared in `gradle/libs.versions.toml`, never inline in `build.gradle`.
- **Nullability uses JSpecify** (`org.jspecify.annotations.Nullable`/`NonNull`), not Jakarta or Spring annotations.
- **New source files need the EPL-2.0 license header** — copy it verbatim from a neighbouring file.
- **`./gradlew spotlessCheck` must pass.** Run `spotlessApply`, then revert files you did not otherwise change.
- **Local verification is `./gradlew unitTests`** (excludes `@Tag("integration")`, needs no Docker). Every test in this plan runs there. Baseline before this plan: 1421 tests.
- **`ServerExceptionResolverTest` is flaky** under parallel execution (global log capture, unrelated to this work). If it is the only failure, re-run before investigating.
- **The server has no CHANGELOG** — reasoning goes in the commit message.
- Stage only files you changed (`git add <path>`), never `git add -A`.

## Decisions taken in this plan

1. **A new controller, not `AdminAPI`.** The spec says "new CRUD endpoints on `AdminAPI`". `AdminAPI.java` is 1552 lines; `RateLimitAPI.java` (610 lines, `@RequestMapping("/admin/ratelimit")`) is the established pattern for a focused admin sub-controller with exactly this CRUD shape. Step 4's webui page clones the `tiers/` frontend, so matching its backend keeps the pair symmetric.
2. **No OpenAPI annotations.** `RateLimitAPI` has none; `AdminAPI` annotates heavily. These endpoints are admin-internal with the webui as the only client, so follow the sibling controller.
3. **`admins.checkAdminUser()` goes *inside* the `try` on every endpoint, including the GET.** `RateLimitAPI` is inconsistent about this — `getTiers` puts it outside, so a non-admin's `ErrorResultException` escapes uncaught, while the mutators turn it into a 403 body. Be consistent; prefer the 403 body.
4. **Write logic lives on `ExtensionSizeLimitService`, not a new service.** Tier CRUD goes controller → `RepositoryService` with no service layer, but tier validation is trivial and ours is not (namespace resolution, optional extension resolution, ceiling check, duplicate check). `ExtensionSizeLimitService` already owns the ceiling and resolution, so the writes are cohesive there and no new class is needed.
5. **The repository is injected directly, not wired through `RepositoryService`.** `ExtensionSizeLimitService` already injects `ExtensionSizeOverrideRepository` directly (as `SettingsCache` does with `SettingRepository`), and `RepositoryService`'s constructor already takes ~40 parameters.
6. **`max-override-size` is added to `PublishingConfig`, and `PublishingConfig` gains the `@PostConstruct` validation it currently lacks.** The property name is `ovsx.publishing.*`, so it belongs there; adding validation to the file we are already editing brings it up to the convention in `server/CLAUDE.md` rather than spreading the non-conformance.
7. **The four deployment descriptors are NOT changed.** `server/CLAUDE.md` warns that a property has four homes that drift — but `ovsx.publishing` appears in **none** of them (`server/src/dev/resources/application.yml`, `deploy/docker/configuration/application.yml`, `deploy/openshift/application.yml`, `deploy/kubernetes/configmap.yaml`). The existing `max-content-size` is undocumented there too; the whole subsection relies on Java defaults. Adding only the new key would be inconsistent with its sibling. Nothing to keep in sync, so nothing to change.
8. **Overrides are addressed by `id`, not by name.** Tiers key on a unique name; an override's identity is the `(namespace, extension)` pair, which does not make a clean path segment. The row already has an `id` and the webui DataGrid will carry it.

## File Structure

| File | Responsibility |
|---|---|
| `server/src/main/java/org/eclipse/openvsx/publish/PublishingConfig.java` | Modify: add `maxOverrideSize` + `validate()` |
| `server/src/main/java/org/eclipse/openvsx/repositories/ExtensionSizeOverrideRepository.java` | Modify: list/find/save/delete |
| `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java` | Modify: public cache invalidation hook |
| `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java` | Modify: create/update/delete + validation |
| `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideJson.java` | New: one override, request and response |
| `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideListJson.java` | New: list response |
| `server/src/main/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPI.java` | New: the four endpoints |

---

### Task 1: Hard-ceiling config property

**Files:**
- Modify: `server/src/main/java/org/eclipse/openvsx/publish/PublishingConfig.java`
- Test: `server/src/test/java/org/eclipse/openvsx/publish/PublishingConfigTest.java` (new — the class has no test today)

**Interfaces:**
- Produces: `PublishingConfig.getMaxOverrideSize(): long` / `setMaxOverrideSize(long)`, bound to `ovsx.publishing.max-override-size`, default 1 GiB. Task 2 consumes it.
- Consumes: nothing new.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/org/eclipse/openvsx/publish/PublishingConfigTest.java`, with the EPL-2.0 header, then:

```java
package org.eclipse.openvsx.publish;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PublishingConfigTest {

    // PublishingConfig binds via @ConfigurationProperties, not @Value: without the properties
    // auto-configuration the bean still registers but nothing binds, and every assertion below
    // would pass against the defaults for the wrong reason.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PublishingConfig.class);

    @Test
    void defaultsMaxOverrideSizeToOneGibibyte() {
        contextRunner.run(
                context -> assertThat(context.getBean(PublishingConfig.class).getMaxOverrideSize())
                        .isEqualTo(1024L * 1024 * 1024));
    }

    @Test
    void bindsMaxOverrideSizeFromTheProperty() {
        contextRunner.withPropertyValues("ovsx.publishing.max-override-size=2147483648")
                .run(
                        context -> assertThat(context.getBean(PublishingConfig.class).getMaxOverrideSize())
                                .isEqualTo(2147483648L));
    }

    @Test
    void refusesANonPositiveMaxOverrideSize() {
        contextRunner.withPropertyValues("ovsx.publishing.max-override-size=0")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("ovsx.publishing.max-override-size"));
    }

    @Test
    void refusesAnOverrideCeilingBelowTheDefaultContentSize() {
        contextRunner
                .withPropertyValues(
                        "ovsx.publishing.max-content-size=1048576",
                        "ovsx.publishing.max-override-size=1024")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("must not be smaller than"));
    }

    @Test
    void refusesANonPositiveMaxContentSize() {
        contextRunner.withPropertyValues("ovsx.publishing.max-content-size=-1")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("ovsx.publishing.max-content-size"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.publish.PublishingConfigTest`

Expected: compile failure — `getMaxOverrideSize()` does not exist.

- [ ] **Step 3: Add the property and the validation**

In `server/src/main/java/org/eclipse/openvsx/publish/PublishingConfig.java`:

Add the import `import jakarta.annotation.PostConstruct;` to the existing import block.

Change the constant and add the new field. Replace:

```java
    private static final int MAX_CONTENT_SIZE = 512 * 1024 * 1024;

    private long maxContentSize = MAX_CONTENT_SIZE;
```

with:

```java
    // long, not int: an int literal for a default above 2 GiB silently overflows.
    private static final long MAX_CONTENT_SIZE = 512L * 1024 * 1024;
    private static final long MAX_OVERRIDE_SIZE = 1024L * 1024 * 1024;

    /**
     * Largest package accepted for publishing when no namespace or extension override applies.
     * <p>
     * Property: {@code ovsx.publishing.max-content-size}
     * Default: {@code 536870912} (512 MiB)
     */
    private long maxContentSize = MAX_CONTENT_SIZE;

    /**
     * Absolute ceiling on any single size override. Validated by the admin API, so no override can
     * raise a namespace's limit past this however it is created.
     * <p>
     * Property: {@code ovsx.publishing.max-override-size}
     * Default: {@code 1073741824} (1 GiB)
     */
    private long maxOverrideSize = MAX_OVERRIDE_SIZE;
```

Add the getter/setter pair next to the existing `getMaxContentSize`/`setMaxContentSize`:

```java
    public long getMaxOverrideSize() {
        return maxOverrideSize;
    }

    public void setMaxOverrideSize(long maxOverrideSize) {
        this.maxOverrideSize = maxOverrideSize;
    }
```

Add `validate()` as the **last** method in the class, matching the house style in `search/SimilarityConfig.java`:

```java
    @PostConstruct
    public void validate() {
        if (maxContentSize <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.publishing.max-content-size must be greater than zero, got: " + maxContentSize);
        }
        if (maxOverrideSize <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.publishing.max-override-size must be greater than zero, got: " + maxOverrideSize);
        }
        if (maxOverrideSize < maxContentSize) {
            throw new IllegalArgumentException(
                    "ovsx.publishing.max-override-size must not be smaller than ovsx.publishing.max-content-size ("
                            + maxContentSize + "), got: " + maxOverrideSize);
        }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.publish.PublishingConfigTest`

Expected: PASS (5 tests).

- [ ] **Step 5: Confirm nothing else broke**

Run: `cd server && ./gradlew unitTests`

`validate()` now runs in every Spring test that loads this bean. Expected: PASS. If a test fails because it sets an invalid combination, fix that test's properties — do not weaken the validation.

- [ ] **Step 6: Format and commit**

Run: `cd server && ./gradlew spotlessCheck` (run `spotlessApply` first if it fails, then revert unrelated files).

```bash
git add server/src/main/java/org/eclipse/openvsx/publish/PublishingConfig.java \
        server/src/test/java/org/eclipse/openvsx/publish/PublishingConfigTest.java
git commit -m "feat: add a hard ceiling for extension size overrides"
```

---

### Task 2: Override writes on `ExtensionSizeLimitService`

**Files:**
- Modify: `server/src/main/java/org/eclipse/openvsx/repositories/ExtensionSizeOverrideRepository.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java`
- Test: `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java` (extend)

**Interfaces:**
- Produces: `ExtensionSizeLimitService.listOverrides(): List<ExtensionSizeOverride>`, `createOverride(String namespaceName, @Nullable String extensionName, long maxSize): ExtensionSizeOverride`, `updateOverride(long id, long maxSize): ExtensionSizeOverride`, `deleteOverride(long id): ExtensionSizeOverride`. All throw `ErrorResultException` on validation failure. Task 3 consumes all four.
- Produces: `SettingsService.invalidateCache(): void`.
- Consumes: `PublishingConfig.getMaxOverrideSize()` (Task 1); existing `RepositoryService.findNamespace(String)` / `findExtension(String, Namespace)`.

**Why the invalidation matters:** `getCeiling()` is `@Cacheable` under its own key and is only evicted by `SettingsCache.clear()`. Without an explicit invalidation on every override write, a new override is invisible to the stream-time cap until the 1-minute TTL expires, and never propagates to other nodes.

- [ ] **Step 1: Write the failing tests**

Append these tests to `server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java`, inside the existing class:

```java
    @Test
    void createRejectsAnUnknownNamespace() {
        when(repositories.findNamespace("nope")).thenReturn(null);

        assertThatThrownBy(() -> limits.createOverride("nope", null, 100L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown namespace");
    }

    @Test
    void createRejectsASizeAboveTheHardCeiling() {
        when(publishingConfig.getMaxOverrideSize()).thenReturn(1000L);
        when(repositories.findNamespace("foo")).thenReturn(namespace("foo", 1L));

        assertThatThrownBy(() -> limits.createOverride("foo", null, 1001L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void createRejectsANonPositiveSize() {
        when(repositories.findNamespace("foo")).thenReturn(namespace("foo", 1L));

        assertThatThrownBy(() -> limits.createOverride("foo", null, 0L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void createRejectsADuplicateScope() {
        when(publishingConfig.getMaxOverrideSize()).thenReturn(10_000L);
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of(override(null, 100L)));

        assertThatThrownBy(() -> limits.createOverride("foo", null, 200L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createRejectsAnExtensionThatIsNotInTheNamespace() {
        when(publishingConfig.getMaxOverrideSize()).thenReturn(10_000L);
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(repositories.findExtension(eq("ghost"), any(Namespace.class))).thenReturn(null);

        assertThatThrownBy(() -> limits.createOverride("foo", "ghost", 200L))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown extension");
    }

    @Test
    void createSavesTheOverrideAndInvalidatesTheCeiling() {
        when(publishingConfig.getMaxOverrideSize()).thenReturn(10_000L);
        var ns = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(ns);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of());
        when(overrides.save(any(ExtensionSizeOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

    @Test
    void updateChangesTheSizeAndInvalidatesTheCeiling() {
        when(publishingConfig.getMaxOverrideSize()).thenReturn(10_000L);
        var existing = override(null, 100L);
        when(overrides.findById(42L)).thenReturn(Optional.of(existing));
        when(overrides.save(any(ExtensionSizeOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var updated = limits.updateOverride(42L, 200L);

        assertThat(updated.getMaxSize()).isEqualTo(200L);
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
```

Add to that file's imports: `java.util.Optional`, `org.eclipse.openvsx.publish.PublishingConfig`, `org.eclipse.openvsx.util.ErrorResultException`, and the static imports `org.assertj.core.api.Assertions.assertThatThrownBy` and `org.mockito.Mockito.verify`.

Add a `publishingConfig` mock field and pass it to the constructor. Replace the existing `setUp()` with:

```java
    private SettingsService settings;
    private ExtensionSizeOverrideRepository overrides;
    private RepositoryService repositories;
    private PublishingConfig publishingConfig;
    private ExtensionSizeLimitService limits;

    @BeforeEach
    void setUp() {
        settings = Mockito.mock(SettingsService.class);
        overrides = Mockito.mock(ExtensionSizeOverrideRepository.class);
        repositories = Mockito.mock(RepositoryService.class);
        publishingConfig = Mockito.mock(PublishingConfig.class);
        Mockito.lenient().when(settings.getMaxExtensionSize()).thenReturn(DEFAULT_LIMIT);
        limits = new ExtensionSizeLimitService(settings, overrides, repositories, publishingConfig);
    }
```

`settings.getMaxExtensionSize()` becomes `lenient()` because the new write tests never reach it, and strict stubs would fail them.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.settings.ExtensionSizeLimitServiceTest`

Expected: compile failure — the four-argument constructor and the four new methods do not exist.

- [ ] **Step 3: Extend the repository**

In `server/src/main/java/org/eclipse/openvsx/repositories/ExtensionSizeOverrideRepository.java`, add these methods to the interface, and add `import java.util.Optional;`:

```java
    List<ExtensionSizeOverride> findAllByOrderByIdAsc();

    Optional<ExtensionSizeOverride> findById(long id);

    ExtensionSizeOverride save(ExtensionSizeOverride override);

    void delete(ExtensionSizeOverride override);
```

These are Spring Data derived/inherited signatures; the interface extends the narrow `Repository<ExtensionSizeOverride, Long>`, so each must be declared explicitly to exist.

- [ ] **Step 4: Add the invalidation hook to `SettingsService`**

In `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java`, add this public method immediately after `updateFromJson`:

```java
    /**
     * Drop every cached setting on this node and tell the other nodes to do the same. Callers that
     * change data the settings cache derives from — notably the size ceiling, which is cached under
     * its own key — must call this; evicting a single key is not enough.
     */
    public void invalidateCache() {
        cache.clear();
        publishSettingsUpdate();
    }
```

- [ ] **Step 5: Add the write methods**

In `server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java`:

Add the imports `java.util.List`, `org.eclipse.openvsx.entities.ExtensionSizeOverride`, `org.eclipse.openvsx.entities.Namespace`, `org.eclipse.openvsx.publish.PublishingConfig`, `org.eclipse.openvsx.util.ErrorResultException`, `org.jspecify.annotations.Nullable`, and `org.springframework.http.HttpStatus`.

Add the field and constructor parameter:

```java
    private final PublishingConfig publishingConfig;
```

```java
    public ExtensionSizeLimitService(
            SettingsService settings,
            ExtensionSizeOverrideRepository overrides,
            RepositoryService repositories,
            PublishingConfig publishingConfig
    ) {
        this.settings = settings;
        this.overrides = overrides;
        this.repositories = repositories;
        this.publishingConfig = publishingConfig;
    }
```

Add these methods after `resolveLimit`:

```java
    public List<ExtensionSizeOverride> listOverrides() {
        return overrides.findAllByOrderByIdAsc();
    }

    public ExtensionSizeOverride createOverride(
            String namespaceName, @Nullable String extensionName, long maxSize) {
        var namespace = requireNamespace(namespaceName);
        var extension = extensionName == null ? null : requireExtension(namespace, extensionName);
        requireValidSize(maxSize);

        var extensionId = extension == null ? null : extension.getId();
        for (var existing : overrides.findByScope(namespace.getId(), extensionId)) {
            var sameScope = extension == null
                    ? existing.getScopeExtension() == null
                    : existing.getScopeExtension() != null;
            if (sameScope) {
                throw new ErrorResultException(
                        "A size override already exists for " + describeScope(namespaceName, extensionName) + ".",
                        HttpStatus.BAD_REQUEST);
            }
        }

        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setScopeExtension(extension);
        override.setMaxSize(maxSize);
        var saved = overrides.save(override);
        settings.invalidateCache();
        return saved;
    }

    public ExtensionSizeOverride updateOverride(long id, long maxSize) {
        var override = requireOverride(id);
        requireValidSize(maxSize);
        override.setMaxSize(maxSize);
        var saved = overrides.save(override);
        settings.invalidateCache();
        return saved;
    }

    public ExtensionSizeOverride deleteOverride(long id) {
        var override = requireOverride(id);
        overrides.delete(override);
        settings.invalidateCache();
        return override;
    }

    private Namespace requireNamespace(String namespaceName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Unknown namespace: " + namespaceName, HttpStatus.BAD_REQUEST);
        }
        return namespace;
    }

    private Extension requireExtension(Namespace namespace, String extensionName) {
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null) {
            throw new ErrorResultException(
                    "Unknown extension: " + describeScope(namespace.getName(), extensionName),
                    HttpStatus.BAD_REQUEST);
        }
        return extension;
    }

    private ExtensionSizeOverride requireOverride(long id) {
        return overrides.findById(id)
                .orElseThrow(
                        () -> new ErrorResultException("Unknown size override: " + id, HttpStatus.NOT_FOUND));
    }

    private void requireValidSize(long maxSize) {
        if (maxSize <= 0) {
            throw new ErrorResultException(
                    "The size override must be greater than zero.", HttpStatus.BAD_REQUEST);
        }
        var ceiling = publishingConfig.getMaxOverrideSize();
        if (maxSize > ceiling) {
            throw new ErrorResultException(
                    "The size override exceeds the maximum of " + ceiling + " bytes.", HttpStatus.BAD_REQUEST);
        }
    }

    private static String describeScope(String namespaceName, @Nullable String extensionName) {
        return extensionName == null ? namespaceName : namespaceName + "." + extensionName;
    }
```

Add `import org.eclipse.openvsx.entities.Extension;` as well, for `requireExtension`'s return type.

- [ ] **Step 6: Fix the other construction site**

`ExtensionSizeLimitService` is constructed directly in tests. Run `cd server && ./gradlew compileTestJava` and pass a `PublishingConfig` mock at each site the compiler reports.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.settings.ExtensionSizeLimitServiceTest`

Expected: PASS (16 tests — the 7 existing plus the 9 added here).

- [ ] **Step 8: Format and commit**

Run: `cd server && ./gradlew spotlessCheck` (run `spotlessApply` first if it fails, then revert unrelated files).

```bash
git add server/src/main/java/org/eclipse/openvsx/repositories/ExtensionSizeOverrideRepository.java \
        server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java \
        server/src/main/java/org/eclipse/openvsx/settings/ExtensionSizeLimitService.java \
        server/src/test/java/org/eclipse/openvsx/settings/ExtensionSizeLimitServiceTest.java
git commit -m "feat: validate and persist extension size overrides"
```

---

### Task 3: Admin endpoints

**Files:**
- Create: `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideJson.java`
- Create: `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideListJson.java`
- Create: `server/src/main/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPI.java`
- Test: `server/src/test/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPITest.java` (new)

**Interfaces:**
- Consumes: `ExtensionSizeLimitService.listOverrides()/createOverride(...)/updateOverride(...)/deleteOverride(...)` (Task 2); `AdminService.checkAdminUser(): UserData`; `LogService.logAction(UserData, ResultJson)`.
- Produces: `GET /admin/size-overrides`, `POST /admin/size-overrides/create`, `PUT /admin/size-overrides/{id}`, `DELETE /admin/size-overrides/{id}`. Step 4's webui page consumes these.

**Note:** `RateLimitAPI` has no test — do not go looking for one to clone. `AdminAPITest` is the model: a plain `@WebMvcTest` that runs under `unitTests` without Docker.

- [ ] **Step 1: Write the JSON types**

Create `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideJson.java`, with the EPL-2.0 header:

```java
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public class SizeOverrideJson extends ResultJson {

    private long id;
    private String namespace;
    private @Nullable String extension;
    private long maxSize;

    public static SizeOverrideJson error(String message) {
        var json = new SizeOverrideJson();
        json.setError(message);
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public @Nullable String getExtension() {
        return extension;
    }

    public void setExtension(@Nullable String extension) {
        this.extension = extension;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
```

Create `server/src/main/java/org/eclipse/openvsx/json/SizeOverrideListJson.java`, with the EPL-2.0 header:

```java
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record SizeOverrideListJson(List<SizeOverrideJson> sizeOverrides) {
}
```

- [ ] **Step 2: Write the failing test**

Create `server/src/test/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPITest.java`, with the EPL-2.0 header:

```java
package org.eclipse.openvsx.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExtensionSizeOverrideAPI.class)
class ExtensionSizeOverrideAPITest {

    @Autowired
    MockMvc mockMvc;

    @MockitoSpyBean
    UserService users;

    @MockitoBean
    ExtensionSizeLimitService limits;

    @MockitoBean
    LogService logs;

    @MockitoBean
    EntityManager entityManager;

    @Test
    void listReturnsEveryOverride() throws Exception {
        mockAdminUser();
        Mockito.when(limits.listOverrides()).thenReturn(java.util.List.of(override(1L, "foo", null, 100L)));

        mockMvc.perform(adminGet("/admin/size-overrides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeOverrides[0].namespace").value("foo"))
                .andExpect(jsonPath("$.sizeOverrides[0].maxSize").value(100))
                .andExpect(jsonPath("$.sizeOverrides[0].extension").doesNotExist());
    }

    @Test
    void listIsForbiddenForANonAdmin() throws Exception {
        mockMvc.perform(adminGet("/admin/size-overrides")).andExpect(status().isForbidden());
    }

    @Test
    void createPersistsTheOverrideAndAuditsIt() throws Exception {
        var admin = mockAdminUser();
        Mockito.when(limits.createOverride("foo", null, 100L)).thenReturn(override(1L, "foo", null, 100L));

        mockMvc.perform(
                        post("/admin/size-overrides/create")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"namespace\":\"foo\",\"maxSize\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    @Test
    void createSurfacesAValidationFailureAsBadRequest() throws Exception {
        mockAdminUser();
        Mockito.when(limits.createOverride(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenThrow(new ErrorResultException("Unknown namespace: nope", BAD_REQUEST));

        mockMvc.perform(
                        post("/admin/size-overrides/create")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"namespace\":\"nope\",\"maxSize\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown namespace: nope"));

        Mockito.verify(logs, Mockito.never()).logAction(Mockito.any(), Mockito.any());
    }

    @Test
    void updateChangesTheSize() throws Exception {
        var admin = mockAdminUser();
        Mockito.when(limits.updateOverride(1L, 200L)).thenReturn(override(1L, "foo", null, 200L));

        mockMvc.perform(
                        put("/admin/size-overrides/1")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"maxSize\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSize").value(200));

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    @Test
    void deleteRemovesTheOverride() throws Exception {
        var admin = mockAdminUser();
        Mockito.when(limits.deleteOverride(1L)).thenReturn(override(1L, "foo", "bar", 100L));

        mockMvc.perform(
                        delete("/admin/size-overrides/1")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(String path) {
        return get(path)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf().asHeader());
    }

    private UserData mockAdminUser() {
        var userData = new UserData();
        userData.setLoginName("admin_user");
        userData.setFullName("Admin User");
        userData.setRole(UserData.Role.ADMIN);
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private ExtensionSizeOverride override(long id, String namespaceName, String extensionName, long maxSize) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        if (extensionName != null) {
            var extension = new Extension();
            extension.setName(extensionName);
            extension.setNamespace(namespace);
            override.setScopeExtension(extension);
        }
        override.setMaxSize(maxSize);
        return override;
    }
}
```

`ExtensionSizeOverride.getId()` has no setter, so the `id` in these fixtures stays 0 — the assertions above deliberately do not depend on it. If `@WebMvcTest` fails to start because the slice needs another collaborator, add it as a `@MockitoBean` rather than widening the slice; `AdminAPITest`'s `@MockitoBean(types = {...})` list shows which beans this context typically needs.

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.admin.ExtensionSizeOverrideAPITest`

Expected: compile failure — `ExtensionSizeOverrideAPI` does not exist.

- [ ] **Step 4: Write the controller**

Create `server/src/main/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPI.java`, with the EPL-2.0 header:

```java
package org.eclipse.openvsx.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.SizeOverrideJson;
import org.eclipse.openvsx.json.SizeOverrideListJson;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;

@RestController
@RequestMapping("/admin/size-overrides")
public class ExtensionSizeOverrideAPI {

    private final Logger logger = LoggerFactory.getLogger(ExtensionSizeOverrideAPI.class);

    private final AdminService admins;
    private final LogService logs;
    private final ExtensionSizeLimitService limits;

    public ExtensionSizeOverrideAPI(AdminService admins, LogService logs, ExtensionSizeLimitService limits) {
        this.admins = admins;
        this.logs = logs;
        this.limits = limits;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SizeOverrideListJson> getSizeOverrides() {
        try {
            admins.checkAdminUser();
            var json = limits.listOverrides().stream()
                    .map(ExtensionSizeOverrideAPI::toJson)
                    .toList();
            return ResponseEntity.ok(new SizeOverrideListJson(json));
        } catch (ErrorResultException exc) {
            // Not exc.toResponseEntity(SizeOverrideListJson.class): that overload is
            // <T extends ResultJson>, and the list type is a record.
            return ResponseEntity.status(exc.getStatus()).build();
        } catch (Exception exc) {
            logger.error("failed retrieving size overrides", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    public ResponseEntity<SizeOverrideJson> createSizeOverride(@RequestBody SizeOverrideJson request) {
        try {
            var adminUser = admins.checkAdminUser();
            var created = limits
                    .createOverride(request.getNamespace(), request.getExtension(), request.getMaxSize());
            var result = toJson(created);
            result.setSuccess("Created size override for " + scopeOf(created) + " at " + created.getMaxSize()
                    + " bytes");
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SizeOverrideJson.class);
        } catch (Exception exc) {
            logger.error("failed creating size override", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    public ResponseEntity<SizeOverrideJson> updateSizeOverride(
            @PathVariable long id, @RequestBody SizeOverrideJson request) {
        try {
            var adminUser = admins.checkAdminUser();
            var updated = limits.updateOverride(id, request.getMaxSize());
            var result = toJson(updated);
            result.setSuccess("Updated size override for " + scopeOf(updated) + " to " + updated.getMaxSize()
                    + " bytes");
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SizeOverrideJson.class);
        } catch (Exception exc) {
            logger.error("failed updating size override {}", id, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    public ResponseEntity<ResultJson> deleteSizeOverride(@PathVariable long id) {
        try {
            var adminUser = admins.checkAdminUser();
            var deleted = limits.deleteOverride(id);
            var result = ResultJson.success("Deleted size override for " + scopeOf(deleted));
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        } catch (Exception exc) {
            logger.error("failed deleting size override {}", id, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static SizeOverrideJson toJson(ExtensionSizeOverride override) {
        var json = new SizeOverrideJson();
        json.setId(override.getId());
        json.setNamespace(override.getScopeNamespace().getName());
        var extension = override.getScopeExtension();
        json.setExtension(extension == null ? null : extension.getName());
        json.setMaxSize(override.getMaxSize());
        return json;
    }

    private static String scopeOf(ExtensionSizeOverride override) {
        var extension = override.getScopeExtension();
        var namespaceName = override.getScopeNamespace().getName();
        return extension == null ? namespaceName : namespaceName + "." + extension.getName();
    }
}
```

`checkAdminUser()` is inside the `try` on all four endpoints, including the GET — deliberately diverging from `RateLimitAPI.getTiers`, which leaves it outside and lets the 403 escape uncaught.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.admin.ExtensionSizeOverrideAPITest`

Expected: PASS (6 tests). Note the GET returns a bare status on `ErrorResultException` while the other three return a typed error body — `toResponseEntity(Class<T>)` is declared `<T extends ResultJson>` (`ErrorResultException.java:65`), which the record list type does not satisfy. That is why `listIsForbiddenForANonAdmin` asserts only the status.

- [ ] **Step 6: Run the full unit tier**

Run: `cd server && ./gradlew unitTests`

Expected: PASS. Remember `ServerExceptionResolverTest` may flake; re-run before investigating.

- [ ] **Step 7: Format and commit**

Run: `cd server && ./gradlew spotlessCheck` (run `spotlessApply` first if it fails, then revert unrelated files).

```bash
git add server/src/main/java/org/eclipse/openvsx/json/SizeOverrideJson.java \
        server/src/main/java/org/eclipse/openvsx/json/SizeOverrideListJson.java \
        server/src/main/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPI.java \
        server/src/test/java/org/eclipse/openvsx/admin/ExtensionSizeOverrideAPITest.java
git commit -m "feat: add admin endpoints for extension size overrides"
```

---

## Self-Review Notes

- **Spec coverage.** Step 3 of the spec's order is "AdminAPI CRUD + LogService audit logging + hard-ceiling validation (`ovsx.publishing.max-override-size`)". Hard ceiling → Task 1; audit via `logs.logAction` after `setSuccess` → Task 3; CRUD → Tasks 2 and 3. The controller placement and the config convention both differ from the spec's letter; see "Decisions taken in this plan".
- **The invalidation gap the spec misses.** `getCeiling()` is cached under its own key and nothing in step 2 evicts it on an override write. Task 2 adds `SettingsService.invalidateCache()` and calls it from all three mutators. Without this, a newly created override would not raise the stream-time cap until the cache TTL expired, so an upload it was meant to permit would still be truncated.
- **Everything here runs without Docker.** All tests are plain unit or `@WebMvcTest` slices, so `./gradlew unitTests` is full verification for this plan — unlike the previous plan, nothing is container-gated.
- **Type consistency.** `createOverride(String, String, long)`, `updateOverride(long, long)`, `deleteOverride(long)` and `listOverrides()` are defined in Task 2 and consumed under those exact names and signatures in Task 3. `getMaxOverrideSize()` is defined in Task 1 and consumed in Task 2.
- **Known residual risk.** Duplicate-scope detection is a check-then-act against the unique index, matching the tier precedent. Two concurrent creates for the same scope can still lose the race, in which case the database constraint rejects the second and the generic handler returns 500 rather than 400. Acceptable for an admin-only endpoint; worth a follow-up if it ever shows up in logs.
- **Not in scope.** The webui page (step 4), the CLI advisory change (step 5) and the public documentation (step 6) remain separate plans. No `created_by` is recorded: mutations are audited into `PersistedLog`, which is why the spec's DDL no longer carries that column.
