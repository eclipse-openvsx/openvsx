# Configurable extension size limits with namespace/extension overrides

Design for [eclipse-openvsx/openvsx#2129](https://github.com/eclipse-openvsx/openvsx/issues/2129): an admin-configurable default upload size limit, plus overrides for verified namespaces and individual extensions, with extension > namespace > default precedence.

## Constraint that shapes the design

The limit is enforced while the publish request body is still streaming, and the namespace is not known until after the upload has finished.

Confirmed in `LocalRegistryService.doPublish` (`server/src/main/java/org/eclipse/openvsx/LocalRegistryService.java:834-903`):

```java
try (var content = new DrainOnCloseInputStream(rawContent, publishingConfig.getMaxContentSize())) {
    var tempFile = extensions.createExtensionFile(content);   // limit enforced here, size-capped read
    try {
        try (var processor = new ExtensionProcessor(tempFile)) {  // namespace known only here (L855)
            ...
            extVersion = extensions.publishVersion(processor, au);
        }
    } catch (RuntimeException exc) { IOUtils.closeQuietly(tempFile); throw exc; }
} catch (IOException e) { throw new ErrorResultException("Failed to read extension file", e); }
```

`ExtensionService.createExtensionFile` (`ExtensionService.java:115-137`) caps the stream at `maxContentSize + 1` bytes and rejects with 413 (`HttpStatus.CONTENT_TOO_LARGE`) if the actual size exceeds `maxContentSize`. Neither publish endpoint (`RegistryAPI.java`, `/api/-/publish` and `/api/user/publish`) takes a namespace path or query parameter — the VSIX body is the only source of identity.

A per-namespace limit therefore cannot be applied up front. The design is two-stage:

1. **Stream-time ceiling** = `max(default, highest configured override)`. Cheap, cacheable as a single number. With no overrides configured it equals the default, so current behavior is unchanged.
2. **Post-parse resolution.** Once `ExtensionProcessor` has parsed the manifest (namespace + extension known), resolve the applicable limit (extension, then namespace, then default) and reject with 413 if the already-written temp file exceeds it.

Trade-off, stated plainly: an oversized upload from a namespace with no override is accepted up to the ceiling before being rejected. That is the cost of the namespace not being in the request. The alternative — a namespace-qualified publish endpoint — is a much larger, client-breaking change and is out of scope here.

## Storage

### Default limit: reuse the existing `Setting` key/value table, unchanged

`Setting` (`server/src/main/java/org/eclipse/openvsx/entities/Setting.java`, migration `V1_69__Setting.sql`) is a flat table today: `id`, `key` (globally unique), `value` (jsonb), `createdAt`, `updatedAt`. Its class Javadoc describes an `entity_type`/`entity_id`-scoped schema, but that scoping **does not exist** in the entity or migration — this is a documentation/implementation mismatch in the current codebase, not something to build on. The flat shape is sufficient for a single global value, so no schema change is needed for the default limit: add a new key, `max-extension-size`.

Precedence between the `Setting` row and `application.yml`'s `ovsx.publishing.max-content-size` (`PublishingConfig`, default 512 MiB):

```
SettingsService.getMaxExtensionSize():
    if a Setting row with key "max-extension-size" exists → return its value
    else → return publishingConfig.getMaxContentSize()   // application.yml fallback
```

One-directional: the DB row, once set via the admin UI/API, shadows `application.yml` for that key on every node (propagated via the existing `settings.update` Redis pub/sub channel, which clears the whole local settings cache on any change — confirmed in `SettingsService`/`SettingsCache`). Editing `application.yml` afterward has no effect unless the `Setting` row is deleted. Until an admin ever touches the new UI, behavior is byte-for-byte what it is today.

This is a new pattern relative to the only existing setting, `read-only` (`SettingsService.SETTING_REGISTRY_READ_ONLY`), which falls back to a hardcoded `false`, not a config-file value — `max-extension-size` is the first setting whose fallback is a `@ConfigurationProperties` value, specifically because operators who already configured `ovsx.publishing.max-content-size` shouldn't have it silently reset the day this feature ships.

### Overrides: new dedicated table, modeled on the existing scoping pattern

Not an extension of `Setting`. Modeled on the FK/cascade pattern already used by `PersonalAccessToken`'s `scope_namespace_id`/`scope_extension_id` columns and the `trusted_publisher` table (`V1_72__Trusted_Publisher.sql`):

```sql
CREATE TABLE extension_size_override (
  id                  BIGINT PRIMARY KEY,
  scope_namespace_id  BIGINT NOT NULL REFERENCES namespace(id) ON DELETE CASCADE,
  scope_extension_id  BIGINT REFERENCES extension(id) ON DELETE CASCADE,  -- NULL = namespace-wide
  max_size            BIGINT NOT NULL,
  created_by_id       BIGINT NOT NULL REFERENCES user_data(id),
  created_at          TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX extension_size_override_scope_idx
  ON extension_size_override (scope_namespace_id, COALESCE(scope_extension_id, 0));
```

`COALESCE(scope_extension_id, 0)` is used instead of `NULLS NOT DISTINCT` to avoid assuming a specific Postgres version; this is the same effect (one row per namespace-wide override, one row per extension-specific override) using a construct that works on any Postgres version already in use by this project.

Migration file: next sequential `V1_76__Extension_Size_Override.sql` in `server/src/main/resources/db/migration/` (latest confirmed as `V1_75__Download_Ingestion_Rename.sql`). Per `server/CLAUDE.md`, migrations are immutable once shipped and `./gradlew jooqCodegen` must be re-run against a live dev DB after this change.

## Resolution and caching

One query per publish, run after `ExtensionProcessor` parses the manifest: fetch both candidate rows for the namespace —

```sql
SELECT * FROM extension_size_override
WHERE scope_namespace_id = :namespaceId
  AND (scope_extension_id = :extensionId OR scope_extension_id IS NULL)
```

— and pick the extension-scoped row if present, else the namespace-scoped row, else `SettingsService.getMaxExtensionSize()`.

Two values are cached through the existing local-cache + Redis-pub/sub mechanism (`SettingsCache`'s `localCacheManager`, invalidated by the `settings.update` channel, which clears the entire local cache on any settings change — confirmed as already a blunt full-clear rather than per-key invalidation, so adding this cache is consistent with existing behavior, not a regression):

- **The ceiling** — a single `long`, `max(getMaxExtensionSize(), MAX(max_size) over all override rows)` — used for the stream-time cap in `ExtensionService.createExtensionFile`.
- **Resolved limit per (namespace, extension)** — used for the post-parse check.

Both invalidate on any write to `Setting` (key `max-extension-size`) or `extension_size_override`, reusing the same channel and full-clear semantics — no new invalidation mechanism needed.

## Enforcement changes

- `ExtensionService.getMaxContentSize()` (`ExtensionService.java:96-98`) is renamed/repurposed to return the cached **ceiling** instead of `publishingConfig.getMaxContentSize()` directly. `PublishingConfig`'s value remains the ultimate bootstrap fallback (see Storage section above) when no `Setting` row exists.
- In `LocalRegistryService.doPublish`, after the `ExtensionProcessor` try-block opens (L849) and before `extensions.publishVersion(processor, au)` is called, resolve the real limit for `(processor.getNamespace(), processor.getExtensionName())` and compare against `tempFile`'s actual size.
  - Over limit: `IOUtils.closeQuietly(tempFile)` (same cleanup path already used in the existing catch block) and throw `ErrorResultException` with `HttpStatus.CONTENT_TOO_LARGE`, with a message naming the applicable limit and a documentation link, per the issue's stated requirement.

## Admin API and audit

New CRUD endpoints on `AdminAPI` for `extension_size_override` (create/update/delete; list is implicit per-namespace or admin-wide for the dashboard). Each mutation follows the existing pattern confirmed in `ExtensionService.purgeExtension` and repeated throughout `AdminService`/`AdminAPI`:

```java
var result = ResultJson.success("Set max extension size for namespace foo to 100MB");
logs.logAction(admin, result);
```

`LogService.logAction` (`server/src/main/java/org/eclipse/openvsx/util/LogService.java`) only persists a `PersistedLog` row when `result.getSuccess() != null` — old/new values are composed directly into that message string, consistent with how every other admin mutation records its audit trail. No separate audit table.

### Hard ceiling on overrides

A fixed absolute maximum, independent of any configured override, validated on both the admin UI and the `AdminAPI` endpoint — e.g. a new `ovsx.publishing.max-override-size` property. Not stored per-row; both sides check against the same constant. `PublishingConfig` does not currently follow the `@Value` + `@PostConstruct`-validation convention used by other `*Config` classes per `server/CLAUDE.md` (it uses bare `@ConfigurationProperties` with no validation) — the new property should pick a convention deliberately rather than copy `PublishingConfig` by default.

### Namespace verification lapsing

Per `RepositoryService.isVerified` (`server/src/main/java/org/eclipse/openvsx/repositories/RepositoryService.java:497-499`, confirmed as `hasMemberships(namespace, ROLE_OWNER)`): if a namespace's last owner is removed, an existing override **stays active** rather than being revoked automatically. The admin dashboard list page shows a "namespace no longer verified" badge (computed from `isVerified` per row) for manual review. This intentionally diverges from the unrelated "trusted publisher" revocation that already exists (`UserService.revokeTrustedPublishers`, triggered on ownership change) — that mechanism governs OIDC trusted-publishing registrations, not namespace-level size overrides, and the two should not be conflated.

## Webui admin page

Cloned from the `tiers/` template (`webui/src/pages/admin-dashboard/tiers/`), confirmed as a clean, minimal pattern to follow:

- `use-size-overrides.ts` — one `useQuery` (key `['admin','sizeOverrides']`) + `useCreateSizeOverride`/`useUpdateSizeOverride`/`useDeleteSizeOverride` mutations, each invalidating the query key on success. Same shape as `use-tiers.ts`.
- `size-overrides.tsx` — `DataGrid` list page: columns namespace, extension (optional, blank = namespace-wide), max size, created by/at, verified-namespace badge, edit/delete actions. "Create override" button opens the form dialog with no selection; edit opens it pre-filled.
- `size-override-form-dialog.tsx` — namespace autocomplete (required), extension autocomplete (optional, scoped to the chosen namespace), a size field using the tier form's value+unit-dropdown pattern (bytes/KB/MB/GB, recombined to bytes on submit) rather than a raw byte count, validated client-side against the hard ceiling in addition to server-side validation.
- `delete-size-override-dialog.tsx` — confirm dialog, same shape as `delete-tier-dialog.tsx`.

The global default (`max-extension-size`) is a single field added to the existing settings page rather than a page of its own — it is one value, not a collection.

## CLI

Confirmed in `cli/src/publish.ts:28-55,72,153-165`: `maxExtensionSize` is fetched once from `/api/version` before any manifest is parsed, and `ensureWithinSizeLimit` throws if the local file exceeds it. With namespace/extension overrides in place, this becomes a false negative — the CLI could refuse an upload the server would accept.

`/api/version` has no namespace context and gains none here (a namespace-aware variant is a larger protocol change, not required for correctness). Instead, the check becomes advisory: `ensureWithinSizeLimit`'s `throw` becomes a `console.warn`, and the upload proceeds. The server's 413 — now carrying the actual applicable limit per the Enforcement section above — is authoritative either way. This is the smallest change that removes the false-negative risk, at the cost of a possibly-wrong local warning, which the issue accepts as preferable.

## Open items not resolved by this design

- **Retroactivity**: an extension override applies to future versions only; nothing re-validates previously stored versions against a tightened limit. Falls out naturally from the design (the limit is only checked at publish time) — flagged here per the issue so nobody later expects pruning of existing oversized versions.
- **`PublishingConfig` validation convention**: left to the implementation to decide (see Hard ceiling section) rather than settled here.

## Suggested implementation order

1. `max-extension-size` `Setting` key + `SettingsService.getMaxExtensionSize()` + settings API/UI field. Ships the admin-configurable default on its own; no behavior change for anyone who doesn't touch it.
2. `extension_size_override` entity + migration (`V1_76__...`) + resolution query + ceiling/per-scope caching + two-stage enforcement in `ExtensionService`/`LocalRegistryService` + 413 message with applicable limit and docs link.
3. `AdminAPI` CRUD + `LogService` audit logging + hard-ceiling validation (`ovsx.publishing.max-override-size`).
4. Webui admin page cloned from `tiers/`.
5. CLI advisory change (`publish.ts`).
6. Public documentation (the 413 message's docs link, the admin-facing explanation of precedence).

## Resolved during design (decisions the issue left open)

- **Namespace verification lapsing**: override stays active; admin page surfaces a "no longer verified" badge for manual review. (See Namespace verification lapsing, above.)
- **Hard ceiling on overrides**: yes — a fixed absolute maximum validated by both the admin UI and the API, independent of any per-namespace override.
