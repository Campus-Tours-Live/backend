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

# Claude skills — when to use what (backend)

This repo is the **core API + data** (`:8080`, Java 21 / Spring Boot / JPA / OAuth2 resource
server / Flyway / PostgreSQL). It is the **contract owner**: `frontend → bff → backend`.
Data shapes and API contracts start here.

Skills are **not** auto-applied every turn — Claude picks them per-message from their
`description`. The table below tells Claude (and reminds humans) which skill fits which
situation. You can always force one via the skill's slash command.

> **One-time setup:** plugins are declared in `.claude/settings.json`
> (marketplace `claude-code-workflows` = `wshobson/agents`). The first time you open this
> repo, accept the workspace-trust dialog so they load.

## Situation → skill

| When you are… | Use this skill |
| --- | --- |
| Planning any new endpoint / behavior change | `superpowers:brainstorming` |
| Spring Boot / Java 21 / layered architecture | `jvm-languages` |
| Designing REST endpoints / DTO contracts | `api-scaffolding` |
| **Spring Security / OAuth2 resource server / JWT validation** | `backend-api-security` |
| Data model / schema / queries / indexes / performance | `database-design` |
| Writing **Flyway migrations** (adding / changing schema) | `database-migrations` |
| Writing / adding tests (JUnit 5 + Testcontainers, incl. ≥80% patch coverage) | `qa-orchestra`, `superpowers:test-driven-development` |
| Checking security (authz, injection, dependency CVEs, OWASP) | `security-scanning`, `backend-api-security` |
| Debugging (any bug / test failure / unexpected behavior) | `superpowers:systematic-debugging` |
| Self-review before finishing | `comprehensive-review`, `/code-review` |

## ⚠️ Cross-repo observation rules (read before changing backend)

backend is the contract origin — **its changes have the largest downstream impact**:

- **Changing API contract / DTO / returned fields** → this always affects **bff**
  (proxy/aggregation reads your shape), then **frontend**. Read how bff consumes it before
  changing, and update bff (and frontend if needed) in lockstep after.
- **Changing OAuth2 / token validation (audience, scope, headers, expiry)** → **bff** is the
  party that issues/forwards tokens, so both sides must agree on the token. Run
  `backend-api-security` for these.
- **Changing DB schema (Flyway migration)** → if a field is exposed by the API, verify layer
  by layer along backend → bff → frontend. **Migrations are forward-only — never edit an
  existing, already-deployed migration file.**
- **Contract-first principle**: new features start with backend **defining the contract**,
  then bff adapts and frontend consumes. Breaking contract changes must be flagged and
  coordinated with the other two repos — never merged unilaterally.

> Rule of thumb: any change that alters "what backend exposes" is the **origin** of a
> cross-repo change — at minimum **read** the corresponding consumer code in bff. See the
> "Cross-repo coordination" section in `campus-tours-live/CLAUDE.md`.
