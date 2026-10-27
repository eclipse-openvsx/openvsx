# AGENTS.md

Operating rules for AI coding agents working anywhere in this repository —
the Eclipse Open VSX registry: `server/` (Spring Boot, Gradle), `webui/` (the
published React component library), and `cli/` (the `ovsx` command line
tool, Node/TypeScript). Claude Code loads this file via `CLAUDE.md`; other
agents read it directly.

This is the short, must-follow contract shared by every component. Each
component directory has its own `AGENTS.md`/`CLAUDE.md` for rules specific to
its stack — read that one too before working there; it takes precedence over
this file where the two disagree. If any rule here conflicts with an explicit
request from the user, ask before overriding it.

## Non-negotiables

- **New source files need the EPL-2.0 license header** (copy it from any
  existing file in the same component).
- **Never commit unless the user asks**, and stage only the files you changed
  (`git add <path>`), never `git add -A` / `git add .`.
- **A change with no matching test update is incomplete**, unless it has no
  testable surface (a docs-only or pure styling/config change) — say so
  explicitly rather than skipping silently; for a bug fix, confirm the
  regression test fails without the fix. See the component's own `AGENTS.md`
  for its test runner and layout.

## Workflow and code quality

- Read files in full before wide-ranging changes, before editing files you
  have not inspected, and when investigating. Do not rely on search snippets.
- Keep code comments short (1–3 lines): state only the non-obvious constraint
  or rationale, never narrate what the code does. In particular do not
  recount the bug a line used to have or how it used to behave: a comment is
  read by someone looking at the code as it stands, and the history belongs
  in the commit message and the pull request, where anyone asking why it
  changed is already looking.
- Ask before removing functionality or code that appears intentional. Do not
  preserve backward compatibility unless the user asks for it.

## Changelog

Each component keeps its own `CHANGELOG.md` (`server/`, `webui/`, `cli/`), in
the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. A change
with user-visible behaviour is incomplete without its entry.

- **Keep entries short — one line per change.** State what changed and why it
  matters to the component's audience (see its `AGENTS.md`); link the
  issue/PR for detail instead of writing the detail out.
- New entries go under `## [next] (unreleased)`, in `### Added`,
  `### Changed`, `### Fixed`, `### Removed` or `### Dependencies`. Read the
  whole section first and append to an existing subsection; add one only if
  it is missing.
- A release commit renames that heading to `## [vX.Y.Z] - YYYY-MM-DD` and
  opens a new `[next]`; don't do that yourself. Released sections are
  immutable.
- **Never describe a change against something that is itself unreleased.** If
  your work modifies behaviour introduced by an entry under `[next]`, edit
  that entry to describe the final state instead of adding a second one:
  readers of the release never saw the intermediate version. A genuinely new
  behaviour still earns its own entry, phrased as the end state.
- Internal-only changes (tests, CI, docs, refactors with no behaviour change)
  get no entry.

## Conventions

- Commit subjects use a conventional-commit prefix: `feat:`, `fix:`,
  `chore:`, `docs:`, `test:`, `style:`, `build:`, or `ci:`.
- Keep the commit body short: state what changed and why, not a walkthrough
  of how you got there or what an earlier draft did.
- No emojis in commits, pull requests, issues, or code. Keep prose concise,
  direct, and technical — no cheerful filler.
- Answer a user's question before making edits or running implementation
  commands.
- When responding to feedback or an analysis, say whether you agree or
  disagree before describing what you changed.

## Git

Multiple sessions may be running in this cwd at the same time, each modifying
different files. Git operations that touch unstaged, staged, or untracked
files outside your own changes will stomp on other sessions' work. Follow
these rules:

Committing:

- Only commit files YOU changed in THIS session.
- Stage explicit paths (`git add <path1> <path2>`); never `git add -A` /
  `git add .`.
- Before committing, run `git status` and verify you are only staging your
  files.

Never run (destroys other agents' work or bypasses checks):

- `git reset --hard`, `git checkout .`, `git clean -fd`, `git stash`,
  `git add -A`, `git add .`, `git commit --no-verify`.

If rebase conflicts occur:

- Resolve conflicts only in files you modified.
- If a conflict is in a file you did not modify, abort and ask the user.
- Never force push.
