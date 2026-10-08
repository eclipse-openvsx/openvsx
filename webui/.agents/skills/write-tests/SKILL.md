---
name: write-tests
description: How to write unit tests for the webui — the vitest + React Testing Library setup, file layout, harness patterns for context/router-dependent code, and the fail-first check that proves a test actually guards its target. Use when adding or updating tests under test/unit.
---

# Writing unit tests (webui)

## Stack

- **vitest** (`yarn test` runs `vitest run`), **jsdom** environment, **React Testing Library** (`@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`).
- Config: `vite.config.mts` → `test.include: ['test/unit/**/*.spec.{ts,tsx}']`, `setupFiles: ['./test/setup.ts']`.
- `test/setup.ts` registers jest-dom matchers and an `afterEach(cleanup)`. **Vitest globals are off**: import `describe/it/expect/vi` from `'vitest'`, and don't add your own RTL cleanup — setup.ts owns it.

## Layout

- Mirror the source path under `test/unit/` (e.g. `src/context/search/…` → `test/unit/context/…`).
- Reusable harnesses/mocks go in `test/unit/support/`.

## Test what you own, not your dependencies

Assert the logic and wiring that belong to this codebase — not behavior a dependency already guarantees. A test whose only assertion is that MUI `Tabs` switch tabs, react-router navigates, or TanStack caches is testing someone else's library; it earns nothing and breaks on their upgrades.

- Exercising that behavior _incidentally_, as a step in a flow that asserts your own logic, is good — click the tab, then assert the panel your code renders. What you don't do is write a dedicated test for the third-party behavior alone.
- When you catch yourself asserting a library did its job, move the assertion to the part you own: the props you passed it, the state/callback you wired, the value you derived from its output.

## Patterns, simplest first

- **Pure functions** → call and assert. See `test/unit/utils.spec.ts`.
- **Components** → `render` + `screen` + jest-dom matchers (`toBeInTheDocument`, …). See `test/unit/components/kbd-key.spec.tsx`. Prefer `@testing-library/user-event` for real interaction over synthetic events.
- **Anything that needs the app's providers** (a TanStack hook reading `service` from `MainContext`, a component using search, keyboard shortcuts, the theme, or routing) → render it through the shared harness `test/unit/support/test-providers.tsx`: `renderWithProviders(ui, { route, mainContext: { service } })` for components, `renderHookWithProviders(hook, …)` for hooks (the idiomatic way to read a hook's return via `result.current`), or `withProviders(Component, opts)` for the HOC form. The harness reuses the app's real `AppProviders` (so it tracks the app automatically — query client, `MainContext`, keyboard shortcuts, search) and adds the entry-shell bits `AppProviders` doesn't own (a fresh no-retry QueryClient, the MUI theme, a `MemoryRouter`). Reach for this first — don't hand-roll a provider stack per spec.
- **Routing / history** → don't mock it. `test-providers` mounts a real `MemoryRouter`; drive back/forward with the `useNavigate()` you pull from the hook under test (`navigate(-1)` / `navigate(1)` are POPs), and pass the starting URL via `route`.
- **Code that reaches a browser API through a context-backed hook** (e.g. `useInView` → `ViewportObserverContext`) → never stub the browser global; pass the hook's testing provider through the harness instead (`renderWithProviders(ui, { viewport: createTestViewport().observer })`, driven with `setInView`). Only the hook's own spec mocks the browser API (`vi.stubGlobal`), and it does so to test the context's default implementation. See `test/unit/hooks/use-in-view.spec.tsx` and `test/unit/support/viewport.ts`.
- **An external dependency whose timing or return you must control** (a slow client, a flaky global) → `vi.mock('pkg', …)` it with a small controllable stand-in, kept generic and reusable in `test/unit/support/`. Reach for this only when the real thing genuinely can't be driven — prefer the real router/providers first.

## Grow the harness, don't duplicate it

The suite gets easier to extend only if shared test code is treated like production code.

- Before writing a mock, stub, or render helper, check `test/unit/support/` for one that already fits and reuse it.
- If an existing helper almost fits, extend or refactor it — kept generic and dependency-agnostic — rather than forking a near-duplicate. A second caller is the signal to generalize, not to copy.
- When an inline mock or helper in a spec would help another test, promote it to `test/unit/support/` before it gets copied.
- Refactoring a shared harness is part of adding the test that needs it: update every call site and leave the whole suite green. Never let two helpers do the same job.
- Don't write tests for the test helpers themselves. A harness or mock in `test/unit/support/` is exercised by the specs that use it; it never gets its own spec.

## The fail-first check (required for regression tests)

A test that passes tells you nothing until you've seen it fail. After writing a test that guards a fix, temporarily break the code it protects, run the test, confirm it goes **red**, then restore. This is the only thing that proves the test is wired to the behavior — it matters most for timing/race fixes, where a naive test passes with or without the fix.

## Run

- One file while iterating: `yarn vitest run test/unit/<path>.spec.tsx`.
- Whole suite: `yarn test`. Iterate on test or implementation until green, then `yarn lint`.
