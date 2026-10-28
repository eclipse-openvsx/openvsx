# AGENTS.md

Operating rules for AI coding agents working on `ovsx` — the Open VSX command
line interface in `src/`, Node/TypeScript, built and tested with yarn. Claude
Code loads this file via `CLAUDE.md`; other agents read it directly.

Read the repo-root `AGENTS.md` first — it has the shared contract (git
safety, commit conventions, comment style, license headers, testing
philosophy). This file only covers what's specific to the CLI.

## Non-negotiables

- **`yarn test` (vitest) and `yarn lint` (eslint) must pass before you
  commit.**
- **`yarn.lock` is the source of truth for dependencies.**

## Project shape

- Each command's logic lives in `src/<command>.ts`, taking a typed options
  object; most (not all — `logout` doesn't) have a sibling
  `src/<command>-options.ts` declaring that options interface. The actual CLI
  flags (`.option(...)`) are declared in `src/main.ts`, which parses them and
  wires each command into `commander`.
- Unit tests live in `test/unit/`, generally one `<command>.spec.ts` per
  command file, occasionally split further by scenario (e.g.
  `registry-download.spec.ts` / `registry-json.spec.ts`).

## Changelog

`CHANGELOG.md`, following the rules in the root `AGENTS.md`. Entries say why a
change matters to a user of the CLI.
