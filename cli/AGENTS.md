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

- Each command lives in its own `src/<command>.ts` plus a sibling
  `src/<command>-options.ts` for its CLI flags, wired up in `src/main.ts`.
- Unit tests live in `test/unit/`, generally one `<command>.spec.ts` per
  command file, occasionally split further by scenario (e.g.
  `registry-download.spec.ts` / `registry-json.spec.ts`).

## Changelog

Location: `CHANGELOG.md`.

- **Keep entries short — one line per change.** State what changed and why it
  matters to a user of the CLI; link the issue/PR for detail instead of
  writing the detail out.
- New entries go under `### [next] (unreleased)`, in `#### Added` /
  `#### Fixed` / `#### Changed` / `### Dependencies` as needed. A release
  commit renames that heading to the version and date — don't do that
  yourself.
