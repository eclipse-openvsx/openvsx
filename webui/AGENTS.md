# AGENTS.md

Operating rules for AI coding agents working on the Open VSX web UI
(`openvsx-webui`) — the published React component library in `src/` plus the
standalone app under `src/default/`. Claude Code loads this file via
`CLAUDE.md`; other agents read it directly.

Read the repo-root `AGENTS.md` first — it has the shared contract (git
safety, commit conventions, comment style, license headers, testing
philosophy). This file only covers what's specific to the webui. The change
workflow lives in the `write-code` skill and test guidance in the
`write-tests` skill.

## Non-negotiables

- **`yarn lint` must pass before you commit.** Full output (no `| tail`); fix
  every error, warning, and info. Never disable a rule or commit with
  `--no-verify`.
- **Every behavioral change ships with a unit test and a changelog entry.**
  Tests are how a change is verified (see the `write-tests` skill); changelog
  entries go under `## [next]` in `CHANGELOG.md`.
- **`yarn.lock` is the source of truth for dependencies.** Direct external deps
  stay pinned to exact versions; treat lockfile changes as reviewed code.

## Project shape

- **Pages/routes:** add a page under `src/pages/<area>/` and its path to that
  area's `*-routes.ts`; build paths with `createRoute` (`src/utils.ts`).
- **Components:** reusable UI goes in `src/components/`; keep design in the MUI
  theme, not inline `sx`, and prefer `rem` over `px`. Break large components
  into smaller, focused ones rather than growing one big component. Prefer a
  `styled` component over inline `sx` once the styling is more than trivial — a
  named styled component reads better than a long `sx` block, even for a single
  use; reserve `sx` for a one-off prop or two.
- **Data fetching** (mid-migration to TanStack Query): wrap the API client in a
  hook rather than calling the service straight from a component. Create a new
  hook in the enclosing folder of the feature that uses it; move it to
  `src/hooks/` only once a second place needs it. Follow the
  `tanstack-query-conventions` skill; migrate an existing endpoint only when the
  user asks, via the `migrate-to-tanstack` skill.
- **Shared state:** cross-component state goes in a context under `src/context/`.
- **Public API:** this package is published — anything consumers should use must
  be re-exported from `src/index.ts`. Keep that surface intentional.

## Changing code requires tests

Every behavioral change updates coverage:

- **vitest** unit tests under `test/unit/`, mirroring the source path
  (`yarn test`). Follow the `write-tests` skill; for a bug fix add a regression
  test and confirm it fails without the fix.
- A change with no matching test update is incomplete. Pure styling/config with
  no testable surface is the only exception — say so explicitly rather than
  skipping silently.

The Playwright smoke test in `test/e2e/` runs only when the user asks
(`yarn smoke-tests`).

## Workflow and code quality

Follow the `write-code` skill: understand → implement → test → changelog → lint.

- No `any` unless absolutely necessary. Check `node_modules` for external API
  types; don't guess.
- **No inline imports** (`await import()`, `import("pkg").Type`, dynamic type
  imports). Top-level imports only.
- Inline single-line helpers that have only one call site.
- Never remove or downgrade code to silence a type error from an outdated
  dependency — upgrade the dependency instead.

## Dependencies

- Install locally with `yarn install`; clean/CI-style with
  `yarn install --immutable`. Don't run lifecycle scripts unless the user asks.

## Changelog

Location: `CHANGELOG.md`.

Sections under `## [next]`: `### Added`, `### Changed`, `### Fixed`, `### Removed`, `### Dependencies`.

Rules:

- **Keep entries short — one line per change.** State what changed and why it
  matters to a consumer of the package; link the issue/PR for detail instead
  of writing the detail out.
- All new entries go under `## [next]`. Read the full section first and append to existing subsections; never duplicate them; if subsection does not exist yet add it.
- Released version sections (e.g. `## [0.5.0]`) are immutable; never modify them.
- **Never describe a change against something that is itself unreleased.** If your
  work modifies behaviour introduced by an entry already under `## [next]`, edit
  that entry to describe the final state instead of adding a second one. Readers
  of the released notes never saw the intermediate version, so "replaced X with
  Y" is noise when X never shipped. Adding a genuinely new behaviour still earns
  its own entry — just phrase it as the end state, not as a delta.
- The same test applies to public API: a signature introduced under `## [next]`
  and changed again before release is one entry describing the final signature,
  not a change entry. Only a signature that has actually shipped is a breaking
  change worth calling out.
