# AGENTS.md

Operating rules for AI coding agents working on the Open VSX registry server —
the Spring Boot application in `src/main/java`, built with Gradle. Claude Code
loads this file via `CLAUDE.md`; other agents read it directly.

Read the repo-root `AGENTS.md` first — it has the shared contract (git
safety, commit conventions, comment style, license headers, testing
philosophy). This file only covers what's specific to the server.

## Persistence: `EntityManager.merge` needs justifying, every time

`merge` copies a **whole detached entity** over the stored row. Any column that
changed since that entity was loaded is silently reverted. It is a write, even
where the surrounding code only means to read.

Never reach for `merge` to obtain a managed entity. Use
`entityManager.find(Type.class, id)` and set the fields you mean to change —
the setters are what persist inside a transaction.

Before writing or keeping a `merge`, answer all five:

1. **Is an update actually intended here?** If the method only reads, or only
   needs the entity managed so a lazy association loads, use `find`.
2. **Is the entity detached?** An already-managed entity makes `merge` a no-op
   that returns the same instance — the call is noise.
3. **How stale is it?** An entity loaded before slow work (VSIX parsing, a
   remote call) and merged afterwards will revert anything a concurrent writer
   changed in between, `active` included.
4. **Does anything depend on instance identity?** `UserData#equals` compares
   every field, so a `merge` can be load-bearing purely by making two
   references `==`. Compare ids instead — and reject an unassigned id, or an
   unpersisted entity makes the check fail open.
5. **Is the row still there?** `find` returns `null` for a deleted row where
   `merge` would try to resurrect it. Handle the `null`.

`merge` before `remove` works but emits a pointless UPDATE ahead of the DELETE.

This is issue #989, reported as a concurrent write to an extension being
reverted on a *read* path, leaving extensions inactive. Treat a new `merge` as
something to argue for in the pull request description.

## Non-negotiables

- **`./gradlew test` must pass before you commit.** Some tests start
  Testcontainers (Postgres, Elasticsearch, LocalStack), so a working Docker
  daemon is required; say so rather than skipping them silently.
- **`./gradlew spotlessCheck` must pass.** It is deliberately not wired into
  `build`/`check` (`enforceCheck = false`), but the tree is clean, so a
  violation it reports is one you introduced. Run `spotlessApply` and **revert
  files you did not otherwise change**, rather than sweeping unrelated
  formatting into your commit.

## Project shape

- **Java 25** (`libs.versions.toml`). Dependencies are declared in
  `gradle/libs.versions.toml`, never inline in `build.gradle`.
- **Nullability is expressed with JSpecify** (`@Nullable`, `@NonNull` from
  `org.jspecify.annotations`), not Jakarta or Spring annotations.
- **`src/main/jooq-gen/` is generated and committed.** Never hand-edit it; it
  is excluded from Spotless. Regenerate it with `./gradlew jooqCodegen` after a
  schema change — that task reads a live Postgres, so it needs the dev database
  running.
- **Flyway migrations** live in `src/main/resources/db/migration` as
  `V<n>__Description.sql`. A migration that has shipped is immutable — fix a
  mistake with a new one. They are excluded from the pre-commit hooks.
- **Configuration properties** are bound in `*Config` classes with `@Value`,
  each documented with its property name and default, and validated in a
  `@PostConstruct`. A property with an invalid value should fail startup rather
  than misbehave later.
- The server has **no CHANGELOG** — only `cli/` and `webui/` do. Do not invent
  one; put the reasoning in the commit message and pull request instead.

## The formatter's versions have one source of truth

The Eclipse formatter is driven from two paths — Spotless (`spotlessApply`) and
jbang (`scripts/format.sh`, which pre-commit runs) — and they format the same
source differently if they load different versions of the same library. Two
coordinates are therefore spread across the build and the jbang scripts, and
must agree:

- **`org.eclipse.jdt.core`** — in `buildSrc/build.gradle`, `scripts/format.sh`
  and the `//DEPS` lines of the two brace-fix scripts. All must match what
  Spotless provisions for the `eclipse('<N>')` step in `build.gradle`. Note
  that not every Eclipse release has a bundled lockfile — moving
  `eclipse('<N>')` to one that does not makes Spotless fall back to live P2
  provisioning, which fails when Eclipse has not populated that repository.
  Beware that JDT uses two version schemes: Eclipse platform releases count
  `4.40`, `4.41`, while the Maven artifacts count `3.46.0`, `3.47.0`.
- **`com.diffplug.spotless:spotless-lib(-extra)`** — in
  `buildSrc/build.gradle` and `ImportSort.java`'s `//DEPS`. All must match the
  version the Spotless plugin in `libs.versions.toml` depends on.

Do not change one by hand. `scripts/formatter-version-check.sh` resolves both
authoritative versions — the JDT pin out of spotless-lib-extra's bundled
lockfile, the spotless-lib version out of the plugin's POM — and compares every
declaration against them; pre-commit runs it whenever one of those files
changes.

## Deployment descriptors travel with the config

A property added, renamed or removed in the server has **four** homes that can
drift apart:

- `src/dev/resources/application.yml`
- `../deploy/docker/configuration/application.yml`
- `../deploy/openshift/application.yml`
- `../deploy/kubernetes/configmap.yaml` (the same document, indented inside
  `data:`)

The Kubernetes one was added later than the others and has twice been missed by
changes that updated the rest, leaving keys that silently bind to nothing. When
you touch configuration, diff all four and say which you changed.

## Changing code requires tests

- JUnit 5 with Mockito and AssertJ under `src/test/java`, mirroring the source
  package.
- For a bug fix, **confirm the test fails without the fix** — a regression test
  that passes either way is not one.
- Prefer a focused unit test over booting the whole context. A config class can
  be exercised with `ApplicationContextRunner`; note that a bare runner has no
  conversion service, so register one
  (`ApplicationConversionService.getSharedInstance()`) or `Duration` and
  collection properties will not bind.

## Configuration properties

A configuration property that has never appeared in a release can be renamed
outright; one that has shipped needs a fallback to its old key (see
`ovsx.access-token.prefix`). Check the tags before assuming either.
