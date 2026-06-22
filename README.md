# CampusToursLive.ai — Core API

The **Core API** for CampusToursLive.ai: the system of record for authentication context,
users & roles, guide / participant onboarding, and the tour-offering supply side. It is a
stateless Spring Boot service that validates a Google OIDC `id_token` on every request.

> The web app (Next.js), the **BFF**, and this **Core API** are **independent services, each in
> its own repository**. This repo is the Core API only. Where it sits in the wider system:
>
> ```
> browser → web app → BFF → Core API (this repo) → PostgreSQL
> ```
>
> The Core is called only by the BFF (`/v1/*` → Core); it is never called by the browser directly.

---

## Contents

- [CampusToursLive.ai — Core API](#campustoursliveai--core-api)
  - [Contents](#contents)
  - [Tech stack](#tech-stack)
  - [Prerequisites](#prerequisites)
  - [Google OAuth setup](#google-oauth-setup)
  - [Getting started](#getting-started)
    - [1. Install the toolchain](#1-install-the-toolchain)
    - [2. Verify the toolchain](#2-verify-the-toolchain)
    - [3. Run](#3-run)
    - [4. Verify it's up](#4-verify-its-up)
  - [Configuration (environment variables)](#configuration-environment-variables)
  - [Testing](#testing)
  - [Database \& migrations](#database--migrations)
  - [Architecture](#architecture)
  - [API overview](#api-overview)
  - [Authentication \& roles](#authentication--roles)
  - [Code quality (format \& coverage)](#code-quality-format--coverage)
  - [Git hooks \& commit conventions](#git-hooks--commit-conventions)
  - [Build \& package](#build--package)
  - [Troubleshooting](#troubleshooting)

---

## Tech stack

| Area             | Choice                                                                            |
| ---------------- | --------------------------------------------------------------------------------- |
| Language / build | **Java 21**, Maven (via the `./mvnw` wrapper)                                     |
| Framework        | **Spring Boot 3.4.7** (Web MVC)                                                   |
| Persistence      | Spring Data JPA + **PostgreSQL 15**; **Flyway** owns the schema (`ddl-auto=none`) |
| Security         | Spring Security **OAuth2 Resource Server** — Google OIDC `id_token` (JWT)         |
| Observability    | Spring Boot **Actuator** (health + k8s liveness/readiness probes)                 |
| Testing          | JUnit 5, Mockito, Spring Test, **Testcontainers** (real Postgres)                 |
| Tooling          | **Spotless** (google-java-format, AOSP) + **JaCoCo** (coverage)                   |
| Misc             | Lombok                                                                            |

---

## Prerequisites

- **JDK 21** and **Docker Desktop** — the install + verification commands are in
  [Getting started](#getting-started) below.
- Maven is **not** needed separately — the bundled `./mvnw` wrapper downloads it on first run.
- A **Google OAuth Client** — create it once (see [Google OAuth setup](#google-oauth-setup)). The
  Core needs only the **Client ID** (the `id_token` audience it enforces); the client secret belongs
  to the BFF. _(Optional for local-only Core — leave `GOOGLE_CLIENT_ID` blank to skip the audience
  check.)_

---

## Google OAuth setup

The platform authenticates with **Google Sign-In**. The BFF runs the OAuth login flow; this Core
service only **validates** the resulting `id_token`. Both share **one** OAuth client.

> ℹ️ The [Google Cloud console](https://console.cloud.google.com/) is mid-redesign, so menu labels
> differ by account. The steps below use the classic **APIs & Services** navigation (left nav:
> _Credentials_, _OAuth consent screen_). Newer consoles put the same screens under
> [**Google Auth platform**](https://console.cloud.google.com/auth) (_Clients_ / _Branding_ /
> _Audience_) — the actions are identical.

**1. Create / select a Google Cloud project**

- In the top-bar project picker, select your project — or click **New project** → name it →
  **Create**.

**2. Configure the OAuth consent screen** _(once per project)_

- **APIs & Services → OAuth consent screen** → **User type: External** → enter the app name,
  user-support email, and contact email → **Save**.
- Add yourself as a **test user**: under **Test users** (on the consent screen, or the **Audience**
  tab) → **Add users** → your Google account. _(In **Testing** mode only listed test users can sign
  in — otherwise sign-in returns `access_denied`.)_

**3. Create the OAuth client (Web application)**

1. **APIs & Services → Credentials → + Create credentials → OAuth client ID**.
2. **Application type → Web application**; give it a **Name** (e.g. `CampusToursLive BFF`).
3. Under **Authorized redirect URIs → Add URI** → `http://localhost:3001/auth/callback` (must equal
   the BFF's `GOOGLE_REDIRECT_URI`). Optionally add `http://localhost:3001` under **Authorized
   JavaScript origins**.
4. Click **Create**. Google shows the **Client ID** and **Client secret**.

> ⚠️ **Save both immediately.** The **Client secret is shown only once** — copy the Client ID _and_
> the secret into a password manager (or _Download JSON_) right away. If you lose it or it leaks,
> **regenerate** the secret in the console (and update wherever it's configured).

**Who uses what — both services must point at the _same_ client:**

| Value             | Core (this repo)                                         | BFF                                                                    |
| ----------------- | -------------------------------------------------------- | ---------------------------------------------------------------------- |
| **Client ID**     | `GOOGLE_CLIENT_ID` — the `id_token` audience it enforces | `GOOGLE_CLIENT_ID` — identifies the app to Google                      |
| **Client secret** | _not used_                                               | `GOOGLE_CLIENT_SECRET` — required to exchange the auth code for tokens |

> The Core checks `id_token.aud == GOOGLE_CLIENT_ID`. If the Core and BFF are configured with
> **different** clients, every authenticated request fails with **401** — keep the Client ID
> identical across both services.

**Never commit these values.** They belong in a git-ignored `.env`, your shell environment, or a
secrets manager — never in the repository.

---

## Getting started

Local dev needs just two tools — **JDK 21** and **Docker** — plus a Google OAuth Client ID.
Follow the steps in order: **install → verify → run**.

### 1. Install the toolchain

**macOS** (Homebrew):

```bash
brew install --cask temurin@21   # JDK 21 (Adoptium Temurin)
brew install --cask docker       # Docker Desktop
```

**Windows** (winget):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Docker.DockerDesktop
```

**Linux / other:** install [JDK 21](https://adoptium.net/temurin/releases/?version=21) and
[Docker Desktop / Engine](https://www.docker.com/products/docker-desktop/) from their sites.

Then **open Docker Desktop once** so the daemon starts (it stays running afterwards). Maven is
**not** needed separately — the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows) downloads it
on first run.

### 2. Verify the toolchain

```bash
java -version     # must show 21.x
docker info       # no error = the Docker daemon is running
./mvnw -v         # in backend/ (Windows: .\mvnw.cmd -v) — shows Maven + "Java version: 21"
```

- `java -version` not 21?
  - macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`, then retry.
  - Windows (PowerShell): set `JAVA_HOME` to the JDK 21 path (e.g. `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21..."`), or via _System Properties → Environment Variables_, then reopen the terminal.
- `docker info` says _"Cannot connect to the Docker daemon"_? → start Docker Desktop, wait for green.
- `./mvnw` _permission denied_? (macOS/Linux) → `chmod +x mvnw`. On Windows just use `mvnw.cmd` (no chmod).

### 3. Run

This repo is self-contained: its `docker-compose.yml` provides the only infra dependency (Postgres).

```bash
docker compose up -d     # start Postgres (this repo's docker-compose.yml, on :5432)
docker compose ps        # wait until ctl-core-postgres is "healthy"
```

Then run the Core. `./mvnw spring-boot:run` **compiles and launches in one step** (no separate build
needed); Flyway applies the schema on startup and the server listens on **:8080**. `GOOGLE_CLIENT_ID`
is the `id_token` audience the Core enforces — see [Configuration](#configuration-environment-variables)
(it can be left blank for local-only dev).

**macOS / Linux:**

```bash
GOOGLE_CLIENT_ID=<your-google-client-id> ./mvnw spring-boot:run
```

**Windows (PowerShell):**

```powershell
$env:GOOGLE_CLIENT_ID="<your-google-client-id>"; .\mvnw.cmd spring-boot:run
```

> The default `DB_URL` already points at `localhost:5432`, matching the bundled compose — no
> override needed. Set `DB_URL` only if you run Postgres elsewhere.

**Hot reload (Spring Boot DevTools).** While `spring-boot:run` is running, **DevTools auto-restarts**
the app whenever the compiled classes change (a fast two-classloader restart, ~1–2s — much quicker
than a cold boot). DevTools watches `target/classes`, so you just need something to recompile on save:

- **In an IDE** (IntelliJ / VS Code with the Java extension) — enable build/compile on save
  (IntelliJ: _Settings → Build, Execution, Deployment → Compiler → Build project automatically_).
  Save a `.java` file → the IDE recompiles → DevTools restarts.
- **Plain terminal** — keep `spring-boot:run` in one terminal and run `./mvnw compile` in another
  after edits to trigger the restart.

DevTools is dev-only: it's excluded from a packaged/`java -jar` run, so it never ships to production.

### 4. Verify it's up

```bash
curl http://localhost:8080/actuator/health           # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' \
     http://localhost:8080/userinfo                  # 401 — security chain is up (unauthenticated)
```

---

## Configuration (environment variables)

The Core reads its settings from the **process environment** — Spring resolves the `${VAR:default}`
placeholders in `application.properties` from OS env vars / system properties. **Spring Boot does
not auto-load a `.env` file** (unlike the Node BFF).

Every variable has a sensible default, so for **local dev against the bundled Postgres you can set
nothing** and it just runs. To override a value, choose one of:

- pass it inline for a single run — `GOOGLE_CLIENT_ID=... ./mvnw spring-boot:run`;
- export it in your shell, or set it in your IDE's run configuration;
- keep a git-ignored `.env` and load it before running — `set -a; source .env; set +a` (macOS/Linux).

The full set of variables (all optional for local dev; some required in prod):

> **Secrets policy:** never commit real secrets. A local `.env`, if you keep one, is git-ignored.
> In production, inject `DB_PASSWORD` / `GOOGLE_CLIENT_ID` from the platform's env config or a
> secrets manager.

| Variable           | Purpose                                                                                                            | Default                                        | Secret?         |
| ------------------ | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------- | --------------- |
| `DB_URL`           | Postgres JDBC URL                                                                                                  | `jdbc:postgresql://localhost:5432/campustours` | no              |
| `DB_USERNAME`      | DB user                                                                                                            | `ctl`                                          | no (local)      |
| `DB_PASSWORD`      | DB password                                                                                                        | `ctl`                                          | **yes in prod** |
| `OIDC_ISSUER_URI`  | OIDC issuer whose JWKS validates the `id_token`                                                                    | `https://accounts.google.com`                  | no              |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID; required in the `id_token` `aud` claim. Blank → audience check skipped (**not for prod**). | _(blank)_                                      | no (public)     |
| `CORE_PORT`        | HTTP port                                                                                                          | `8080`                                         | no              |

---

## Testing

```bash
./mvnw test          # unit + slice + repository tests (Surefire)
./mvnw verify        # the above + JaCoCo coverage report + Spotless check (Recommended)
```

- **Docker must be running** — `UniversityRepositoryTest` uses **Testcontainers** to spin up a
  real Postgres (the schema relies on PG enums + `jsonb`, which H2 can't emulate).
- Coverage report: `target/site/jacoco/index.html` (written by `verify`).
- Test layers: pure **Mockito** unit tests (services / `CurrentUser`), `@WebMvcTest` slice tests
  (controllers + problem+json mapping), and `@DataJpaTest` + Testcontainers (Flyway + repositories).

---

## Database & migrations

- **Flyway owns the schema** (`spring.jpa.hibernate.ddl-auto=none`); Hibernate never alters it.
- Migrations live in `src/main/resources/db/migration/`:

  | File                        | Purpose                                                             |
  | --------------------------- | ------------------------------------------------------------------- |
  | `V1__schema.sql`            | The complete baseline schema (tables, enums, constraints, indexes). |
  | `V2__seed_universities.sql` | Seeds the university catalog (~100 rows, idempotent).               |

- **Conventions:** `V<n>__<snake_case>.sql`, applied in ascending order. Migrations are
  **immutable history** — once a version is applied anywhere you do **not** edit or delete it;
  you add a new `V<n+1>`.
- **Reset a local DB** (after a schema change, or a Flyway checksum error):

  ```bash
  docker compose down -v && docker compose up -d   # drops the volume + flyway_schema_history
  ```

---

## Architecture

**Layered**, with the `domain` layer organized **package-by-feature** (controllers live in `web/`,
DTOs in `web/dto/`). Rooted at `com.CampusToursLive`:

```
src/main/java/com/CampusToursLive/
├── BackendApplication.java        # Spring Boot entry point
├── config/                        # WebConfig, etc.
├── security/                      # SecurityConfig, JWT/audience validation, CurrentUser, provisioning
├── domain/                        # business logic, by feature
│   ├── user/                      #   users, user_roles, RoleGrantService
│   ├── guide/                     #   guide profiles, verification, approval
│   ├── participant/               #   participant profiles
│   ├── tour/                      #   tour offerings (supply side)
│   └── university/                #   university catalog
├── web/                           # @RestControllers
│   └── dto/                       #   request/response records + ApiEnvelope
└── error/                         # framework-agnostic domain exceptions
```

Key principles:

- **Layering:** `web` (controllers) → `domain` (services + repositories + entities); `security`
  is a cross-cut. Controllers are thin and delegate to services.
- **Errors:** the domain throws framework-agnostic exceptions (`ValidationException`,
  `NotFoundException`, `ForbiddenException`, `UnauthorizedException`); `GlobalExceptionHandler`
  is the single place that maps them to **RFC 7807 `application/problem+json`** (422/404/403/401).
- **DTOs:** responses are immutable `record`s (e.g. `MeResponse`, `TourOfferingResponse`) — no
  stringly-typed maps on the wire.
- The deeper multi-role / role-switching design is captured in the project's design docs; the role
  model is summarized under [Authentication & roles](#authentication--roles).

---

## API overview

The BFF maps `/v1/*` → these Core paths. Every successful response uses the `{ data, meta }`
envelope; errors are `application/problem+json`.

| Method        | Path                              | Purpose                                                   |
| ------------- | --------------------------------- | --------------------------------------------------------- |
| `GET`         | `/actuator/health`                | Liveness/readiness (Actuator)                             |
| `GET`         | `/userinfo`                       | Current principal (`MeResponse`)                          |
| `POST`        | `/session`                        | Resolve/provision the account by intent (signin / signup) |
| `POST`        | `/session/active-role`            | Switch the UX active role (PARTICIPANT ↔ GUIDE)           |
| `GET` `PATCH` | `/participant/profile`            | Read / upsert the participant profile                     |
| `GET` `PATCH` | `/guide/profile`                  | Read / upsert the guide profile (+ submit application)    |
| `GET` `POST`  | `/guide/offerings`                | List / create a guide's tour offerings                    |
| `POST`        | `/guide/offerings/{id}/activate`  | Publish a draft offering (requires an APPROVED guide)     |
| `GET`         | `/universities`                   | University catalog search (`q`, `limit`)                  |
| `GET`         | `/meta/tour-topics`               | Controlled vocabulary for tour topics                     |
| `POST`        | `/admin/guides/{userId}/decision` | Admin approve/reject a guide application                  |

---

## Authentication & roles

- **AuthN:** every request carries a Google OIDC `id_token` as a Bearer JWT (forwarded by the BFF).
  The Core validates it against Google's JWKS (signature + issuer + expiry) and, when
  `GOOGLE_CLIENT_ID` is set, the `aud` claim (see `AudienceValidator`). There is **no local auth
  stub**.
- **Identity:** the token's `sub` claim maps to `users.oidc_subject`. Accounts are provisioned
  just-in-time on first sign-up (a "bare" account with no role).
- **AuthZ:** authorization always reads the authoritative `user_roles` set (via
  `CurrentUser.requireRole(...)`), **never** `users.last_active_role` (which is UX context only).
  Roles: `PARTICIPANT`, `GUIDE`, `ADMIN`, `SUPPORT`.

---

## Code quality (format & coverage)

```bash
./mvnw spotless:apply     # auto-format (google-java-format, AOSP / 4-space) — run before committing
./mvnw spotless:check     # CI-style check (also bound to `verify`)
./mvnw verify             # runs tests + JaCoCo coverage gate
```

- **Spotless** enforces formatting; run `spotless:apply` once before your first `verify`.
- **JaCoCo** enforces a coverage gate on the in-scope code — bundle **line, branch, and method
  coverage each ≥ 90%** (the Spring bootstrap, config/security wiring, DTO records, and JPA
  entities are excluded; see the `<excludes>` in `pom.xml`). Actual coverage is 100%.

---

## Git hooks & commit conventions

The repo enforces formatting, tests, and a commit-message convention through **git hooks**, kept in
sync with Maven so every contributor gets them automatically (the parallel of the BFF's husky
setup). The `git-build-hook-maven-plugin` points git's `core.hooksPath` at the version-controlled
`.githooks/` directory on any build.

**Enable once** (after cloning) — run any Maven goal, or explicitly:

```bash
./mvnw validate            # runs the plugin's `configure` goal → sets core.hooksPath=.githooks/
```

The hooks (`.githooks/`, all POSIX `sh`):

| Hook         | Runs                | Purpose                                                      |
| ------------ | ------------------- | ----------------------------------------------------------- |
| `pre-commit` | `./mvnw spotless:check` | Blocks the commit if code isn't formatted (fix: `spotless:apply`). |
| `commit-msg` | message-format check | Enforces the commit convention below.                       |
| `pre-push`   | `./mvnw verify`     | Runs the full test suite + coverage gate before pushing.    |

**Commit message format** (identical to the BFF):

```
<type>: <BOARD>-<NUMBER> <description>
```

- `<type>` — one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
  `chore`, `revert` (an optional scope is allowed: `fix(security): …`).
- `<BOARD>` — the Jira board key, uppercase (e.g. `CTL`).
- `<NUMBER>` — the Jira ticket number.

Examples: `feat: CTL-1234 validate id_token audience`, `fix(security): CTL-987 reject expired JWT`.
Commits missing the type or the `<BOARD>-<NUMBER>` ticket are rejected.

> **Monorepo note:** `core.hooksPath` resolves relative to the git repo root. While this Core lives
> inside the umbrella monorepo (single `.git` at the root), the hooks bind to the repo root, not
> `backend/`. Once the Core is its own repository (its root = this directory), the hooks apply
> exactly as described. To bypass a hook in an emergency, use `git commit --no-verify` /
> `git push --no-verify`.

---

## Build & package

```bash
./mvnw clean package                          # → target/backend-0.0.1-SNAPSHOT.jar
java -jar target/backend-0.0.1-SNAPSHOT.jar   # run the fat jar
```

(Container images are not built yet — Jib / buildpacks are a planned addition.)

---

## Troubleshooting

| Symptom                                                                        | Cause & fix                                                                                                                                                                                      |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `Connection to localhost:5432 refused` / Hikari timeout                        | Postgres isn't up. `docker compose up -d`, wait for healthy (`docker compose ps`), then (re)start the Core.                                                                                      |
| Flyway `Detected applied migration not resolved locally` / `checksum mismatch` | The DB has migration history that no longer matches local files (e.g. after a schema change). Reset: `docker compose down -v && docker compose up -d`.                                           |
| `/session` returns **500** on login                                            | Usually the Core can't reach the DB, or the schema is stale. Check the console where `./mvnw spring-boot:run` is running for the real exception; reset the DB if it's a schema/connection error. |
| Testcontainers: _"Could not find a valid Docker environment"_                  | Docker daemon not running, or a Docker-Desktop API-version mismatch. The pom pins `docker.api.version=1.44` for docker-java; ensure Docker Desktop is running and the default socket is enabled. |
| `./mvnw verify` fails on **Spotless**                                          | Code isn't formatted. Run `./mvnw spotless:apply`, then re-run.                                                                                                                                  |
| 401 on every authenticated call                                                | Expected when unauthenticated. Real calls come from the BFF (a separate service) forwarding a valid Google `id_token`; check that the BFF's `GOOGLE_CLIENT_ID` matches this service's.           |

---

> **Principle:** _"it runs" beats "it looks nice."_ If you only read one section, read
> [Getting started](#getting-started).
