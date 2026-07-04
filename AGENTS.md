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

# Agent skills — when to use what (backend)

This guide covers **both Codex and Claude Code**. This repo is the **core API + data** (`:8080`,
Java 21 / Spring Boot / JPA / OAuth2 resource server / Flyway / PostgreSQL). It is the **contract
owner**: `frontend → bff → backend`. Data shapes and API contracts start here.

Skills are **not** auto-applied every turn — the agent picks them per-message from their
`description`. The table below steers that choice. To force a skill, invoke **its own slash
command** (e.g. `/code-review`).

> **Setup is automatic — for both agents.** This repo's plugins are declared in
> `.claude/settings.json`; `.claude/hooks/ensure-plugins.mjs` installs and keeps them updated for
> whichever agent CLI you have (`claude` and/or `codex`). The same plugin ids work for both — the
> `wshobson/agents` marketplace ships dual `.claude-plugin` + `.codex-plugin` manifests.
> - **Claude Code** — a `SessionStart` hook (every session) runs the script and emits
>   `reloadSkills`, so a first-time install is usable in the **same** session (from the first
>   prompt). Accept the workspace-trust dialog once so they load.
> - **Codex** — Codex has no per-repo SessionStart auto-install, so its trigger is the launcher
>   (`npm run start:backend` / `start:all`) or `codex plugin add <name>@claude-code-workflows`.
>   `qa-orchestra` isn't in the Codex snapshot — for tests use `unit-testing` /
>   `api-testing-observability`.
> - **Cursor (2.5+)** — no plugin CLI or auto-install; install once **in the editor** (add
>   `wshobson/agents`, then `/plugin install <name>`). A committed `.cursor/rules/agent-skills.mdc`
>   gives Cursor the per-repo guidance automatically; Cursor doesn't honor a skill's `tools:` allowlist.
>
> `pom.xml` and the Maven build are intentionally untouched. Both agents also keep enabled
> plugins **updated to latest** (throttled to ~once/day; update everything now with the launcher's
> `npm run update:skills`).
>
> **`†` = process skill (Claude-only).** Rows marked `†` (`superpowers:*`, `doc-coauthoring`)
> come from the **user-level** `superpowers` / `example-skills` plugins — Claude-only, installed
> once at the user level (see `campus-tours-live/AGENTS.md` → "One-time setup"). **Codex does not
> have these**; in Codex, follow the same discipline (plan before coding, TDD, systematic
> debugging) with its built-in flow. Everything unmarked is a domain skill auto-installed for both
> agents.

## Situation → skill

| When you are… | Use this skill |
| --- | --- |
| Planning any new endpoint / behavior change | `superpowers:brainstorming` † |
| Refactoring (no behavior change) | `superpowers:brainstorming` †, then `comprehensive-review` |
| Spring Boot / Java 21 / layered architecture | `jvm-languages` |
| Designing REST endpoints / DTO contracts | `api-scaffolding` |
| **Spring Security / OAuth2 resource server / JWT validation** | `backend-api-security` |
| Data model / schema / queries / indexes / query performance | `database-design` |
| Writing **Flyway migrations** (adding / changing schema) | `database-migrations` |
| Docker / Postgres container / local infra | `database-migrations`, `database-design` (compose lives in this repo; `docker compose up -d`) |
| Logging / observability / Actuator / health & metrics | `api-testing-observability` |
| Env / config changes (`GOOGLE_CLIENT_ID`, DB URL — **no `.env` auto-load**) | ⚠️ cross-repo & startup-critical; see Cross-repo rules below |
| Writing / adding tests (JUnit 5 + Testcontainers) | `qa-orchestra` (Claude) / `unit-testing` + `api-testing-observability` (Codex), `superpowers:test-driven-development` † |
| Dependency upgrades / CVE remediation | `security-scanning` |
| Checking security (authz, injection, dependency CVEs, OWASP) | `security-scanning`, `backend-api-security` |
| Fixing a red CI / failing build | `superpowers:systematic-debugging` † (reproduce locally: `./mvnw verify`) |
| Debugging (any bug / test failure / unexpected behavior) | `superpowers:systematic-debugging` † |
| Writing docs / README / OpenAPI | `doc-coauthoring` † |
| Reviewing your own or someone else's PR, before merging | `comprehensive-review`, `/code-review`; security via `/security-review` |
| **"Live" real-time tours (WebSocket/streaming backend support)** | ⚠️ product core, **no skill and no infra yet** — always plan/`superpowers:brainstorming` † and design before coding |

> **Coverage gate:** this repo's `verify` enforces **JaCoCo ≥90% bundle** coverage (line,
> branch, and method) on in-scope code — not a patch-coverage percentage. See `pom.xml`
> (`<excludes>`) and the README. Run `./mvnw spotless:apply` before your first `verify`.

## ⚠️ Cross-repo observation rules (read before changing backend)

backend is the contract origin — **its changes have the largest downstream impact** (full
matrix in `campus-tours-live/AGENTS.md`):

- **Changing API contract / DTO / returned fields** → this always affects **bff**
  (proxy/aggregation reads your shape), then **frontend**. Read how bff consumes it before
  changing, and update bff (and frontend if needed) in lockstep after. Prefer **additive /
  backward-compatible** changes (add new, deprecate old) so bff/frontend don't break during the
  deploy window — see the hub's "Backward-compatible contract changes".
- **Changing OAuth2 / token validation (audience, scope, headers, expiry)** → **bff** is the
  party that issues/forwards tokens, so both sides must agree on the token. `GOOGLE_CLIENT_ID`
  must match bff, and the OAuth client lives in the Google Console — see the hub's "Cross-repo
  environment contract". Run `backend-api-security` for these.
- **Changing DB schema (Flyway migration)** → if a field is exposed by the API, verify layer
  by layer along backend → bff → frontend. **Migrations are forward-only — never edit an
  existing, already-deployed migration file** (add a new one to roll forward/back).
- **Contract-first principle**: new features start with backend **defining the contract**,
  then bff adapts and frontend consumes. Breaking contract changes must be flagged and
  coordinated with the other two repos — never merged unilaterally.
- **If you only cloned backend** → you can't read bff/frontend locally, but you own the
  contract, so define it here and coordinate downstream via OpenAPI / an issue. Clone the
  siblings (`npm run clone-all` in campus-tours-live) when you need to verify consumers.

> Rule of thumb: any change that alters "what backend exposes" is the **origin** of a
> cross-repo change — at minimum **read** the corresponding consumer code in bff, and verify
> end-to-end with the launcher (`npm run start:all`). The full cross-repo coordination rules are
> in `campus-tours-live/AGENTS.md`.

## Labels

**Change-failure labels** — when a PR **fixes something a recent change broke**, label the fix PR so
the weekly DORA report can compute change-failure rate / MTTR:

- `hotfix` — urgent fix for a broken/failed change
- `revert` — reverts a bad change
- `rollback` — rolls back a deployment
- `incident` — tied to a production incident

Put the label on the **fix PR**, not the original. `bug` is for general bug reports/fixes
(informational — not counted as a change failure).

**Size labels** — `size/S` · `size/M` · `size/L` · `size/XL` are **auto-applied** by the
`pr-size-label` workflow from the diff size; you don't add them yourself. Smaller PRs review faster
— aim for `size/S`/`size/M`, and split `size/L`/`size/XL` when you can.
