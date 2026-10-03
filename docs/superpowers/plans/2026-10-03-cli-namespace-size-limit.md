# CLI Namespace-Aware Size Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `ovsx publish` check a package against the size limit that actually applies to its namespace, instead of the registry-wide default, and refuse an oversized upload again rather than merely warning.

**Architecture:** A new authenticated endpoint `GET /api/-/size-limit?namespace=…&extension=…` answers "what limit would apply if I published this", using the existing `ExtensionSizeLimitService.resolveLimit`. The CLI already parses the VSIX manifest, so it knows the namespace and extension locally; it reads the manifest unconditionally, looks the limit up after its token is resolved, and blocks when the package exceeds it. Registries that predate the endpoint fall back to today's advisory warning.

**Tech Stack:** Spring Boot (Java 25), JUnit 5 + Mockito + AssertJ, MockMvc; Node/TypeScript, vitest.

**Spec:** `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md`. This supersedes the CLI section (§128-132), which assumed `/api/version` was the only thing the CLI could ask and therefore settled for an advisory warning. It isn't: the CLI parses the manifest, so a namespace-aware answer is available to it in a way it is not available to the browser.

## Global Constraints

- **Server:** `./gradlew unitTests` and `./gradlew spotlessCheck` must pass. Baseline before this plan: 1443 tests.
- **CLI:** `yarn test` and `yarn lint` must pass. Baseline: 123 tests. `yarn` is not on PATH — run `node .yarn/releases/yarn-4.9.1.cjs <cmd>` from `cli/`. Dependencies must be installed first (`node .yarn/releases/yarn-4.9.1.cjs install --immutable`).
- **CLI changelog:** one short line under `### [next] (unreleased)`, in `#### Changed`.
- **Nullability** uses JSpecify; new Java files need the EPL-2.0 header.
- **No `any`** in CLI TypeScript unless unavoidable.
- `ServerExceptionResolverTest` flakes under parallel execution; re-run before investigating it.
- Stage only files you changed; never `git add -A`.

## Decisions taken in this plan

1. **A query endpoint, not a resource path.** `/api/{namespace}/{extension}/size-limit` would 404 on a first publish, when neither the namespace nor the extension exists yet — which is exactly when the CLI asks. `/api/-/size-limit` sits with the other non-resource operations (`/api/-/search`, `/api/-/query`, `/api/-/publish`) and answers a hypothetical.
2. **A new `AccessTokenAction.VerifyPublishVersion` variant.** Neither existing variant works: `Verify()` leaves `namespace()`/`extension()` empty, so `AccessTokenScope.NamespaceScoped`/`ExtensionScoped` refuse it — and trusted-publishing tokens are extension-scoped, so the TPT flow would 401. `PublishVersion` matches scope but `isUsing()` is `true`, which bumps the accessed timestamp and **deletes a one-time token** before the real publish uses it. The new variant matches scope without consuming.
3. **Authenticated, and permission-checked when the namespace exists.** A valid token alone is a weak barrier — anyone can register and mint one. When the namespace exists the caller must also have publish permission for it; when it does not exist there is nothing to protect and the default is returned. Note this is stricter than `verify-pat`, which uses `Verify()` and therefore ignores token scope entirely.
4. **Version gating, not 404 sniffing, for older registries.** `unpublish.ts:102-110` already gates on `semver.coerce` + `lt` against a minimum version, and `getRegistryVersion()` is cached per `Registry`, so the check is free. A 404 is ambiguous — it can mean a misconfigured reverse proxy rather than an old registry.
5. **Both query parameters are required.** The CLI always has both. An optional `extension` would need a second code path for a question nothing currently asks.
6. **No per-(namespace, extension) cache in the CLI.** A fan-out of target platforms shares one extension, so it is one extra cheap GET per package against an upload of that package. The existing shared `/api/version` lookup is kept, because the fallback path still needs the default.

## File Structure

| File | Responsibility |
|---|---|
| `server/src/main/java/org/eclipse/openvsx/accesstoken/AccessTokenAction.java` | Modify: add `VerifyPublishVersion` |
| `server/src/main/java/org/eclipse/openvsx/json/SizeLimitJson.java` | New: `{ maxSize }` response |
| `server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java` | Modify: `getSizeLimit(...)` |
| `server/src/main/java/org/eclipse/openvsx/RegistryAPI.java` | Modify: the endpoint |
| `cli/src/registry.ts` | Modify: `getSizeLimit(...)` |
| `cli/src/publish.ts` | Modify: read manifest first, check after token resolution |
| `cli/CHANGELOG.md` | Modify: one `#### Changed` line |

---

### Task 1: The endpoint

**Files:**
- Modify: `server/src/main/java/org/eclipse/openvsx/accesstoken/AccessTokenAction.java`
- Create: `server/src/main/java/org/eclipse/openvsx/json/SizeLimitJson.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/RegistryAPI.java`
- Test: `server/src/test/java/org/eclipse/openvsx/LocalRegistryServiceTest.java` (extend)

**Interfaces:**
- Produces: `GET /api/-/size-limit?namespace=…&extension=…` returning `{"maxSize": <bytes>}`; `LocalRegistryService.getSizeLimit(String namespace, String extension, String tokenValue): SizeLimitJson`. Task 2 consumes the endpoint.
- Consumes: `ExtensionSizeLimitService.resolveLimit(String, String)`; `AccessTokenService.useAccessToken(String, AccessTokenAction)`; `UserService.hasPublishPermission(UserData, Namespace)`.

- [ ] **Step 1: Write the failing tests**

Append to `server/src/test/java/org/eclipse/openvsx/LocalRegistryServiceTest.java`, inside the class:

```java
    @Test
    void sizeLimitRejectsAnInvalidToken() {
        when(tokens.useAccessToken(eq("bad"), any())).thenReturn(null);

        assertThatThrownBy(() -> registryService.getSizeLimit("foo", "bar", "bad"))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * A first publish asks about a namespace that does not exist yet, so this cannot 404 or demand a
     * permission there is nobody to hold. The default is the honest answer.
     */
    @Test
    void sizeLimitReturnsTheDefaultForANamespaceThatDoesNotExistYet() {
        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(repositories.findNamespace("new")).thenReturn(null);
        when(sizeLimits.resolveLimit("new", "bar")).thenReturn(4096L);

        assertThat(registryService.getSizeLimit("new", "bar", "tok").getMaxSize()).isEqualTo(4096L);
    }

    @Test
    void sizeLimitRefusesANamespaceTheTokenMayNotPublishTo() {
        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
        var namespace = new Namespace();
        namespace.setName("foo");
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(users.hasPublishPermission(any(), eq(namespace))).thenReturn(false);

        assertThatThrownBy(() -> registryService.getSizeLimit("foo", "bar", "tok"))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sizeLimitReturnsTheResolvedLimitForAPermittedNamespace() {
        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
        var namespace = new Namespace();
        namespace.setName("foo");
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(users.hasPublishPermission(any(), eq(namespace))).thenReturn(true);
        when(sizeLimits.resolveLimit("foo", "bar")).thenReturn(123L);

        assertThat(registryService.getSizeLimit("foo", "bar", "tok").getMaxSize()).isEqualTo(123L);
    }

    /**
     * The token must not be consumed: a one-time PAT checked here and then deleted would leave the
     * publish it was checked for unable to authenticate.
     */
    @Test
    void sizeLimitChecksTheTokenWithoutUsingIt() {
        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(repositories.findNamespace("foo")).thenReturn(null);

        registryService.getSizeLimit("foo", "bar", "tok");

        var action = ArgumentCaptor.forClass(AccessTokenAction.class);
        verify(tokens).useAccessToken(eq("tok"), action.capture());
        assertThat(action.getValue().isUsing()).isFalse();
        assertThat(action.getValue().namespace()).contains("foo");
        assertThat(action.getValue().extension()).contains("bar");
    }
```

Add the imports this needs: `org.mockito.ArgumentCaptor`, `org.eclipse.openvsx.accesstoken.AccessTokenAction`, and whatever of `PersonalAccessToken`/`PersonalAccessTokenType`/`AccessTokenAuthentication`/`Namespace`/`UserData` the file does not already import. If `users` is not already a `@Mock` in this class, add it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.LocalRegistryServiceTest`

Expected: compile failure — `getSizeLimit` does not exist.

- [ ] **Step 3: Add the token action**

Read `server/src/main/java/org/eclipse/openvsx/accesstoken/AccessTokenAction.java` in full first — it is a sealed interface, so the `permits` clause (if explicit) must list the new record.

Add:

```java
    /**
     * Checks whether a token could publish this extension, without using it. Unlike
     * {@link Verify}, the namespace and extension are reported, so a scoped token is matched against
     * the scope it actually has; unlike {@link PublishVersion}, {@code isUsing()} is false, so the
     * token's accessed timestamp is untouched and a one-time token survives the check.
     */
    record VerifyPublishVersion(String namespaceName, String extensionName) implements AccessTokenAction {

        @Override
        public boolean isUsing() {
            return false;
        }

        @Override
        public Optional<String> namespace() {
            return Optional.of(namespaceName);
        }

        @Override
        public Optional<String> extension() {
            return Optional.of(extensionName);
        }
    }
```

- [ ] **Step 4: Add the response type**

Create `server/src/main/java/org/eclipse/openvsx/json/SizeLimitJson.java`, with the EPL-2.0 header, following `SizeOverrideJson`'s shape:

```java
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(Include.NON_NULL)
public class SizeLimitJson extends ResultJson {

    @Schema(description = "Maximum package size in bytes that may be published to this namespace/extension")
    private long maxSize;

    public static SizeLimitJson error(String message) {
        var json = new SizeLimitJson();
        json.setError(message);
        return json;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
```

- [ ] **Step 5: Add the service method**

In `server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java`, add next to `verifyToken` (around line 818), mirroring its shape:

```java
    public SizeLimitJson getSizeLimit(String namespaceName, String extensionName, String tokenValue) {
        var tau = tokens.useAccessToken(
                tokenValue, new AccessTokenAction.VerifyPublishVersion(namespaceName, extensionName));
        if (tau == null) {
            throw new ErrorResultException(ACCESS_TOKEN_ERROR, HttpStatus.UNAUTHORIZED);
        }

        // A first publish names a namespace that does not exist yet; there is no permission to check
        // and nothing to protect, so the default applies.
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace != null && !users.hasPublishPermission(tau.userData(), namespace)) {
            throw new ErrorResultException(
                    "Insufficient access rights for namespace: " + namespace.getName(), HttpStatus.FORBIDDEN);
        }

        var json = new SizeLimitJson();
        json.setMaxSize(sizeLimits.resolveLimit(namespaceName, extensionName));
        return json;
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd server && ./gradlew unitTests --tests org.eclipse.openvsx.LocalRegistryServiceTest`

Expected: PASS (16 tests — the 11 existing plus the 5 added here).

- [ ] **Step 7: Add the endpoint**

In `server/src/main/java/org/eclipse/openvsx/RegistryAPI.java`, add next to the other `/api/-/` endpoints, modelled on `verifyToken` (`:130-171`):

```java
    @GetMapping(path = "/api/-/size-limit", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(
        summary = "Get the maximum package size that may be published to a namespace/extension"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The applicable size limit is returned in JSON format",
        content = @Content(schema = @Schema(implementation = SizeLimitJson.class))
    )
    @ApiResponse(
        responseCode = "401",
        description = "The token is missing, invalid or expired",
        content = @Content(schema = @Schema(implementation = SizeLimitJson.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "The token is valid but has no publishing permission in the namespace",
        content = @Content(schema = @Schema(implementation = SizeLimitJson.class))
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SizeLimitJson> getSizeLimit(
            HttpServletRequest request,
            @RequestParam
            @Parameter(description = "Namespace", example = "redhat") String namespace,
            @RequestParam
            @Parameter(description = "Extension name", example = "java") String extension,
            @RequestParam(required = false)
            @Parameter(description = TOKEN_PARAM_DESCRIPTION, deprecated = true) String token
    ) {
        var tokenValue = HttpHeadersUtil.resolveAccessToken(request, token);
        try {
            return ResponseEntity.ok(local.getSizeLimit(namespace, extension, tokenValue));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SizeLimitJson.class);
        }
    }
```

Do not add a `cacheControl` — the answer is per-namespace and behind auth, so it must not be cached publicly.

- [ ] **Step 8: Verify and commit**

Run: `cd server && ./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew unitTests`

Expected: PASS. Investigate any regression rather than adjusting assertions.

```bash
git add server/src/main/java/org/eclipse/openvsx/accesstoken/AccessTokenAction.java \
        server/src/main/java/org/eclipse/openvsx/json/SizeLimitJson.java \
        server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java \
        server/src/main/java/org/eclipse/openvsx/RegistryAPI.java \
        server/src/test/java/org/eclipse/openvsx/LocalRegistryServiceTest.java
git commit -m "feat: add an endpoint reporting the size limit for a namespace"
```

---

### Task 2: The CLI

**Files:**
- Modify: `cli/src/registry.ts`
- Modify: `cli/src/publish.ts`
- Modify: `cli/CHANGELOG.md`
- Test: `cli/test/unit/publish.spec.ts` (extend)

**Interfaces:**
- Consumes: `GET /api/-/size-limit` (Task 1); `readVSIXPackage` (`cli/src/zip.ts:72-80`), which returns a `Manifest` whose `publisher`/`name` are the namespace/extension; `Registry.getRegistryVersion()`, cached per instance.
- Produces: no new public CLI surface — `publish` behaviour only.

- [ ] **Step 1: Write the failing tests**

In `cli/test/unit/publish.spec.ts`, replace the test added for the advisory warning (`warns but still uploads when the package exceeds the size limit the registry reports`) with:

```ts
    it('refuses a package above the limit the registry reports for its namespace', async () => {
        const registry = await givenRegistry({
            body: { version: '1.3.0', maxExtensionSize: 1024 },
            sizeLimit: 100
        });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('rejected');
        expect(registry.publishRequests).toHaveLength(0);
    });

    it('publishes a package the namespace limit allows even though it exceeds the default', async () => {
        const registry = await givenRegistry({
            body: { version: '1.3.0', maxExtensionSize: 100 },
            sizeLimit: 1024
        });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
    });

    // ovsx talks to registries of every age. A release that refused to publish anywhere without the
    // new endpoint would be worse than the false negative it is fixing.
    it('falls back to warning when the registry predates the size-limit endpoint', async () => {
        const registry = await givenRegistry({ body: { version: '1.2.0', maxExtensionSize: 100 } });
        const extensionFile = givenExtensionFile(200);

        const [result] = await publish({ extensionFile, pat: 'the.pat', registryUrl: registry.url });

        expect(result.status).toBe('fulfilled');
        expect(registry.publishRequests).toHaveLength(1);
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('exceeds the default size limit'));
    });
```

`givenRegistry` must learn to answer `/api/-/size-limit`. Read its current implementation in that spec file and extend it: when a `sizeLimit` option is given, respond `{"maxSize": <sizeLimit>}` to a request whose path starts with `/api/-/size-limit`; when it is not given, answer 404 so the older-registry path is exercised. Keep the existing `/api/version` behaviour untouched.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd cli && node .yarn/releases/yarn-4.9.1.cjs test test/unit/publish.spec.ts`

Expected: FAIL — the CLI never requests `/api/-/size-limit`, so the first two tests see the old behaviour.

- [ ] **Step 3: Add the registry call**

In `cli/src/registry.ts`, add next to `verifyPat` (`:81-88`), following it exactly:

```ts
    async getSizeLimit(namespace: string, extension: string, pat: string): Promise<SizeLimitResponse> {
        try {
            const query = { namespace, extension, ...(await this.tokenQuery(pat)) };
            const url = this.getUrl(['api', '-', 'size-limit'], query);
            return await this.getJson(url, this.tokenHeaders(pat));
        } catch (err) {
            return rejectError(err);
        }
    }
```

Declare the response type next to the other response interfaces in that file:

```ts
export interface SizeLimitResponse {
    maxSize: number;
    error?: string;
}
```

Match the file's existing conventions where this snippet and the surrounding code disagree — read `verifyPat` and its neighbours first.

- [ ] **Step 4: Restructure the publish flow**

In `cli/src/publish.ts`:

Add the minimum version constant next to the other module constants, following `unpublish.ts`'s precedent:

```ts
const MIN_SIZE_LIMIT_REGISTRY_VERSION = '1.3.0';
```

`doPublish` currently reads the manifest only when no `--pat` was supplied, and runs the size check *before* that. Restructure it so the manifest is read unconditionally and the size check runs *after* the token is resolved — at that point a token always exists, whether supplied, fetched by `getPAT`, or exchanged by `getTrustedPublishingToken`. Concretely, replace the body between the packaging block and `doRegistryPublish` with:

```ts
    const manifest = await readVSIXPackage(options.extensionFile!);

    // Set only when this publish obtained the token itself through trusted publishing, which is the one
    // case where a refusal can be answered by asking for a new token.
    let exchanged: { namespace: string; extension: string } | undefined;
    if (!options.pat) {
        if (useTrustedPublishing(options)) {
            exchanged = { namespace: manifest.publisher, extension: manifest.name };
            options.pat = await getTrustedPublishingToken(registry, manifest.publisher, manifest.name, options);
        } else {
            options.pat = await getPAT(manifest.publisher, options);
        }
    }

    // After the token is resolved, so the limit can be looked up for this namespace specifically.
    await ensureWithinSizeLimit(registry, options, manifest);
```

Replace `warnIfAboveSizeLimit` with:

```ts
/**
 * Refuses a package the registry would reject anyway, before uploading it.
 *
 * The limit is the one that applies to this package's namespace and extension, which the registry
 * resolves: a namespace with a size override may publish more than the default allows. Registries
 * older than {@link MIN_SIZE_LIMIT_REGISTRY_VERSION} cannot answer that, so the check falls back to
 * warning against the default rather than refusing an upload those registries might well accept.
 */
async function ensureWithinSizeLimit(
    registry: Registry,
    options: InternalPublishOptions,
    manifest: Manifest
): Promise<void> {
    const { size } = await fs.promises.stat(options.extensionFile!);
    const limit = await resolveSizeLimit(registry, options, manifest);

    if (limit === undefined) {
        const fallback = options.maxExtensionSize;
        if (fallback && size > fallback) {
            console.warn(
                `The extension package (${formatBytes(size)}) exceeds the default size limit of ${formatBytes(fallback)} `
                + `reported by the registry at ${registry.url}. Publishing anyway: the namespace may have a higher `
                + `limit configured, and the registry decides.`
            );
        }
        return;
    }

    if (size > limit) {
        throw new Error(
            `The extension package (${formatBytes(size)}) exceeds the size limit of ${formatBytes(limit)} `
            + `for ${manifest.publisher}.${manifest.name} at ${registry.url}.`
        );
    }
}

/** The limit for this namespace/extension, or `undefined` when the registry cannot report one. */
async function resolveSizeLimit(
    registry: Registry,
    options: InternalPublishOptions,
    manifest: Manifest
): Promise<number | undefined> {
    const reportedVersion = (await registry.getRegistryVersion().catch(() => undefined))?.version;
    const version = reportedVersion ? semver.coerce(reportedVersion) : undefined;
    if (!version || semver.lt(version, MIN_SIZE_LIMIT_REGISTRY_VERSION)) {
        return undefined;
    }

    try {
        const response = await registry.getSizeLimit(manifest.publisher, manifest.name, options.pat!);
        return response.maxSize;
    } catch {
        // The registry claims to be new enough but could not answer; the server enforces the limit
        // regardless, so publish rather than refusing on a lookup failure.
        return undefined;
    }
}
```

Add the imports this needs: `semver`, `Manifest` (from wherever `readVSIXPackage`'s return type is exported). Follow `unpublish.ts`'s import of `semver`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd cli && node .yarn/releases/yarn-4.9.1.cjs test test/unit/publish.spec.ts`

Expected: PASS. Then run the whole suite: `node .yarn/releases/yarn-4.9.1.cjs test` — expected PASS, baseline 123 tests plus the ones added here.

- [ ] **Step 6: Changelog**

In `cli/CHANGELOG.md`, replace the `#### Changed` entry added for the advisory warning with one describing the end state — per the repo rule, an unreleased entry is edited rather than contradicted:

```markdown
- `publish` checks a package against the size limit that applies to its own namespace, which a namespace override can raise above the registry default, and refuses an oversized upload before sending it. Against a registry older than 1.3.0, which cannot report that limit, it warns against the default and publishes anyway ([#2129](https://github.com/eclipse-openvsx/openvsx/issues/2129))
```

- [ ] **Step 7: Lint and commit**

Run: `cd cli && node .yarn/releases/yarn-4.9.1.cjs lint`

```bash
git add cli/src/registry.ts cli/src/publish.ts cli/test/unit/publish.spec.ts cli/CHANGELOG.md
git commit -m "feat(cli): check the package against its namespace's size limit"
```

---

### Task 3: Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md`
- Modify: `doc/configuration.md`

- [ ] **Step 1: Correct the spec's CLI section**

§128-132 concluded the CLI could only warn, because `/api/version` has no namespace context. That reasoning does not hold: the CLI parses the manifest and therefore knows the namespace locally, unlike the browser. Rewrite that section to describe what was built — the authenticated `/api/-/size-limit` query, the hard refusal, and the version-gated fallback for older registries — and say explicitly that the browser still cannot do this, because nothing client-side parses the VSIX, which is why the web UI gates on the ceiling instead.

- [ ] **Step 2: Document the endpoint for operators**

In `doc/configuration.md`, in the "Extension size limits" section, replace the closing paragraph about the CLI with a description of the end state: the CLI asks the registry for the limit that applies to the package's namespace and refuses an oversized upload locally, falling back to a warning against the default when the registry is too old to answer. Mention that the lookup is authenticated and requires publish permission for an existing namespace.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-03-extension-size-limits-design.md doc/configuration.md
git commit -m "docs: describe the namespace-aware size limit lookup"
```

---

## Self-Review Notes

- **Why a new token action was unavoidable.** `Verify()` reports no namespace or extension, so `AccessTokenScope.NamespaceScoped`/`ExtensionScoped` refuse it — and trusted-publishing tokens are extension-scoped, so the TPT flow would 401 at exactly the point this feature is meant to help. `PublishVersion` matches scope but is a *using* action: it bumps the accessed timestamp and deletes a one-time token, which would leave the publish it was checked for unable to authenticate. Task 1 Step 1's last test pins both properties.
- **Why the restructure makes auth free.** The check moves to after token resolution, where a token always exists however it was obtained. That is the same move the namespace lookup requires, so requiring a PAT costs nothing extra.
- **The fallback is load-bearing.** `ovsx` publishes to many registries; without the version gate this release would break publishing against every one that predates the endpoint. Task 2 Step 1's third test pins it.
- **Type consistency.** `getSizeLimit(namespace, extension, tokenValue)` is defined in Task 1 and consumed under that name in Task 2; `SizeLimitJson.maxSize` serialises to the `maxSize` the CLI's `SizeLimitResponse` reads.
- **Known residual risk.** The limit can change between the check and the publish. The server re-checks at publish time and remains authoritative, so the window costs at most one wasted upload or one avoidable refusal.
- **Not in scope.** The web UI keeps gating on the ceiling: nothing client-side parses the VSIX, so the browser cannot know the namespace before uploading. Adding a zip parser there was considered and rejected as a dependency plus client-side duplication of server logic.
