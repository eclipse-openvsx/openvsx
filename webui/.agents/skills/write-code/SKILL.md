---
name: write-code
description: The workflow for changing webui source code — understand, implement, test, changelog, lint. Use whenever writing or modifying source (not for docs-only edits). Enforces that every behavioral change ships with tests and a changelog entry.
---

# Changing webui code

## 1. Understand

- Read the files you're about to change in full before wide-ranging edits, and before editing a file you haven't inspected. Don't work from search snippets alone.
- Check `node_modules` for external API types instead of guessing.

## 2. Implement

- Match the style of the surrounding code.
- Keep comments short (1–3 lines): state the non-obvious constraint or rationale, never narrate what the code does.
- Reach browser APIs (`IntersectionObserver`, `matchMedia`, `ResizeObserver`, …) only through a custom hook, and have that hook talk to the API through a context whose default value is the real implementation — so the app mounts no provider and tests swap in a controllable one. Ship a controllable fake of that context value with it in `test/unit/support/` (the `write-tests` skill covers how specs use it). Model: `useInView` over `ViewportObserverContext` (`src/context/viewport-observer-context.ts`).
- Ask before removing functionality that looks intentional. Don't preserve backward compatibility unless asked.

## 3. Test — required, not optional

Every behavioral change ships with tests. This is a gate, not a nicety:

- New behavior → add a test for it. Changed behavior → update the affected tests.
- Bug fix → add a **regression test** and apply the fail-first check: confirm it goes red without the fix, then restore. A fix without a test that pins it is incomplete.
- Follow the **`write-tests`** skill for setup, layout, and harness patterns.
- Run the file you touched, then the suite (`yarn test`); iterate until green.

If a change genuinely has no unit-testable surface (pure styling, config), say so explicitly rather than skipping silently.

Tests are how a change is verified — a passing, fail-first-checked test is the evidence the change works.

## 4. Changelog

Add an entry for the change under `## [next]` in `CHANGELOG.md`, in the right subsection (`### Added` / `### Changed` / `### Fixed` / `### Removed` / `### Dependencies`) — see AGENTS.md for the format rules. This covers product code; test-only, docs, and tooling changes don't get an entry.

## 5. Lint

`yarn lint` (full output, no `| tail`). Fix every error, warning, and info before considering the change done. Never run `yarn smoke-tests` unless asked. Never commit unless asked.
