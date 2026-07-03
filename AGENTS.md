# Agent & contributor guide

Conventions in this repo are **enforced by CI** — skipping them blocks the merge.
If you (human or AI agent) open a PR, follow these.

## Pull requests

Fill out the PR description using `.github/pull_request_template.md`. The required
`pr-template` check needs:

- a non-empty **## Summary**
- a non-empty **## Testing** section
- at least one **## Type of change** box checked (`- [x]`)

The template is **not** auto-applied when a PR is created via `gh pr create` or by an
agent, so pass a `--body` that includes those sections yourself.

## Commits

Conventional Commits **plus a Jira ticket**:

    <type>: <BOARD>-<NUMBER> <description>
    e.g. feat: CTL-1234 add Google OIDC callback

Types: `feat fix docs style refactor perf test build ci chore revert`.
Enforced by a local `commit-msg` hook (installed on first `./mvnw` / `npm install`).

## What blocks a merge

- `ci` — unit + integration tests, project coverage gate, and ≥80% patch coverage on changed lines
- `pr-template` — the PR-description checks above
- a pull request is required (no direct push to `main`) with **1 approving review**

---

# Agent skills — Codex & Claude (backend)

This repo's skills work for **both Codex and Claude Code**, from the `claude-code-workflows`
marketplace (`wshobson/agents`, dual Codex + Claude plugin manifests).

**Auto-install (both agents).** `.claude/hooks/ensure-plugins.mjs` installs and keeps updated
the enabled plugins for whichever CLI you have — `claude` and/or `codex`. For Claude it runs on
every session (SessionStart hook); for Codex the trigger is the launcher (`npm run start:backend`
/ `start:all`) or a manual install. `pom.xml` / the Maven build are untouched. Install everything
now with `npm run update:skills` (from `campus-tours-live`).

> **Codex specifics.** Codex has no per-repo SessionStart auto-install, so its trigger is the
> launcher or `codex plugin add <name>@claude-code-workflows`. `qa-orchestra` isn't in the Codex
> snapshot — for tests use `unit-testing` / `api-testing-observability`. Process skills
> (`superpowers:*`) are Claude-only — in Codex, follow the same discipline (plan before coding,
> TDD, systematic debugging) with its built-in flow.

**Which skill for which situation, and the cross-repo rules (this repo owns the REST/DTO + OAuth2
token contracts), live in `CLAUDE.md`** — Codex reads `CLAUDE.md` as well. The domain skill names
there (`jvm-languages`, `database-design`, `database-migrations`, `api-scaffolding`,
`backend-api-security`, `security-scanning`, `comprehensive-review`) are identical for both
agents; only `qa-orchestra` and the `superpowers:*` rows differ for Codex (see above).
