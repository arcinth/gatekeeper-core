# Installing GateKeeper

This document walks through setting up GateKeeper for local development: cloning the repository, starting PostgreSQL, running the backend and frontend, and the environment variables that control each part of the system.

For a general overview of what GateKeeper does, see [README.md](README.md). For the system design behind these components, see the documents linked at the end of this guide.

## Prerequisites

| Software | Version | Why |
|---|---|---|
| Java (JDK) | 21 | `backend/pom.xml` targets `<java.version>21</java.version>`; the backend won't compile or run on an older JDK. |
| Node.js | 20 or later | Required by the frontend's build tooling (Vite 8, React 19). |
| Docker | any recent version | Runs PostgreSQL via `docker-compose.yml`, and is required by the backend's Testcontainers-based integration tests. |
| PostgreSQL | 16 | Provided by the `postgres:16-alpine` image in `docker-compose.yml`. A separately managed PostgreSQL 16 instance works too, as long as it's reachable. |

You do not need to install Maven separately. The repository ships the Maven Wrapper (`backend/mvnw` and `mvnw.cmd`), pinned to Maven 3.9.9 in `backend/.mvn/wrapper/maven-wrapper.properties`. Use `./mvnw` (or `mvnw.cmd` on Windows) for every Maven command in this guide.

## Repository Setup

```bash
git clone <repository-url>
cd gatekeeper-core
```

The repository is organized as two independent applications sharing one root:

```
backend/                 Spring Boot API (Java 21, Maven)
frontend/                React + TypeScript SPA (Vite)
docs/                    Product vision, architecture, domain model, and API design documents
docker-compose.yml       PostgreSQL, and an optional containerized backend
.env.example             Template for docker-compose's environment variables
infrastructure/          Reserved for infrastructure-as-code; currently empty
scripts/                 Reserved for operational scripts; currently empty
```

`infrastructure/` and `scripts/` exist in the repository but have no content yet — worth knowing so you don't go looking for deployment scripts that aren't there.

The backend and frontend are set up separately; nothing in this guide requires them to be built together.

## Database Setup

`docker-compose.yml` defines a single `postgres` service (`postgres:16-alpine`), backed by a named volume (`postgres-data`) so data survives container restarts, with a health check (`pg_isready`) that other services can depend on.

Start it with:

```bash
docker compose up -d postgres
```

By default this starts PostgreSQL on host port 5433 (mapped to the container's standard 5432) with database `gatekeeper` and user/password `gatekeeper`/`gatekeeper` — the same defaults the backend's own `application.yml` expects, so the two line up without any configuration on a clean checkout. Port 5433 rather than the Postgres-standard 5432 is deliberate: 5432 is a common default for other, unrelated local Postgres instances, and picking 5433 means GateKeeper's container doesn't fight another project for the port. If you want different credentials or a different port, copy `.env.example` to `.env` and edit `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, or `DB_PORT`; `docker compose` reads `.env` automatically.

One thing to know about that named volume: PostgreSQL only applies `POSTGRES_USER`/`POSTGRES_PASSWORD` the *first* time it initializes an empty data directory. If you start the container once, then change the credentials in `.env` and restart, PostgreSQL won't pick up the change — the old credentials are already baked into the volume. Remove the volume (`docker compose down -v`) if you need to reset credentials from scratch.

### Migrations

Schema management is handled by Flyway (`spring.flyway.enabled: true` in `application.yml`), reading versioned SQL scripts from `backend/src/main/resources/db/migration`. There are eight migrations as of this release:

```
V1__init_schema.sql                        organizations, roles, users, repositories
V2__github_integration.sql                 GitHub App installations, pull requests
V3__policy_findings.sql                    policy findings
V4__analysis_run_and_finding_indexes.sql    indexes for the analysis run / findings queries
V5__security_findings.sql                  security findings
V6__ai_review.sql                          AI review runs and findings
V7__verdicts.sql                           verdicts and verdict reasons
V8__engineering_reports.sql                engineering reports and the audit log
```

These run automatically on backend startup — there is nothing to apply by hand. `baseline-on-migrate: true` is also set, which matters if you ever point the application at a database that already has other tables in it: without it, Flyway refuses to run against a non-empty schema it doesn't recognize.

Nothing else needs to be created manually. The one row that gets seeded automatically (not through a migration, but through application code on startup) is the bootstrap administrator account — see [Bootstrap Administrator](#bootstrap-administrator) below.

## Backend Setup

```bash
cd backend
./mvnw spring-boot:run
```

The first run downloads dependencies into your local Maven repository; subsequent runs are faster. If you'd rather pre-fetch dependencies without running anything, `./mvnw dependency:go-offline` does that.

To run the packaged jar instead of `spring-boot:run` (closer to how the Docker image built by `backend/Dockerfile` runs it):

```bash
./mvnw clean package -DskipTests
java -jar target/gatekeeper-core.jar
```

### How configuration is loaded

The backend's configuration lives in `backend/src/main/resources/application.yml`, with three profile overlays: `application-local.yml` (the default — verbose SQL logging), `application-dev.yml` (quieter, no SQL logging), and `application-prod.yml` (warning-level logging, Swagger disabled, and the startup validators described below). The active profile is controlled by `SPRING_PROFILES_ACTIVE`, defaulting to `local`.

Every configurable value in `application.yml` is written as `${ENV_VAR:default}` — standard Spring property placeholder syntax. That means an actual environment variable always overrides the committed default, and nothing needs to change in the YAML itself to reconfigure a deployment.

One thing this project does *not* do: the backend does not read a `.env` file directly. `.env` (and `.env.example`) only feed `docker-compose.yml`'s own variable substitution. If you run `./mvnw spring-boot:run` or `java -jar` outside of `docker compose`, environment variables need to come from your shell, your IDE's run configuration, or `export`/`set` — not from a `.env` file sitting in the repository root.

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

This starts the Vite dev server on port 5173 (hardcoded in `vite.config.ts`).

The frontend talks to the backend through `apiClient.ts`, which reads `import.meta.env.VITE_API_BASE_URL` and falls back to `http://localhost:8080/api/v1` if it isn't set. There's no `frontend/.env.example` in the repository, so if you need to point the frontend at a backend running somewhere other than `localhost:8080`, create `frontend/.env.local` yourself with:

```
VITE_API_BASE_URL=http://your-backend-host:8080/api/v1
```

This is plain Vite environment variable handling — nothing custom was built for it.

## Environment Variables

All variables below are read by the backend (`application.yml`). None are required to get a working local setup — every one has a default that works for local development out of the box. The groups below reflect what each variable actually affects, not a required/optional split.

**Core (database, server, auth)**

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | Selects `local`, `dev`, or `prod`. |
| `DB_URL` | `jdbc:postgresql://localhost:5433/gatekeeper` | JDBC connection string. |
| `DB_USERNAME` | `gatekeeper` | Database user. |
| `DB_PASSWORD` | `gatekeeper` | Database password. |
| `SERVER_PORT` | `8080` | Backend HTTP port. |
| `JWT_SECRET` | a committed development value | Signing key for access/refresh tokens. |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime. |
| `JWT_REFRESH_TTL_DAYS` | `7` | Refresh token lifetime. |
| `JWT_CLOCK_SKEW_SECONDS` | `30` | Tolerance for clock drift between instances/NTP when validating a token's issued-at/expiry (Milestone 10: Security Hardening). |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origins allowed by the CORS filter. |

**Bootstrap administrator**

| Variable | Default | Purpose |
|---|---|---|
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@gatekeeper.local` | Email of the seeded administrator account. |
| `BOOTSTRAP_ADMIN_PASSWORD` | `ChangeMe123!` | Its password. See the section below — this one matters. |

**GitHub integration** — optional. Without these, the backend still runs; there's just no way to receive a real webhook or fetch pull request data.

| Variable | Default | Purpose |
|---|---|---|
| `GITHUB_APP_ID` | `0` (treated as unset) | GitHub App ID. |
| `GITHUB_APP_PRIVATE_KEY` | empty | GitHub App private key, PKCS#8 PEM format, as the raw PEM content. GitHub issues PKCS#1 by default — convert with `openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in <downloaded-key>.pem -out <pkcs8-key>.pem`. |
| `GITHUB_APP_PRIVATE_KEY_PATH` | empty | Path to a PEM file on disk, as an alternative to `GITHUB_APP_PRIVATE_KEY` — preferred for real use, since a multi-line key is awkward as one env var value. Takes precedence over `GITHUB_APP_PRIVATE_KEY` when set. See `secrets/README.md`. |
| `GITHUB_WEBHOOK_SECRET` | a committed development value | Used to verify the `X-Hub-Signature-256` header on inbound webhooks. |
| `GITHUB_API_BASE_URL` | `https://api.github.com` | Overridable for testing against a mock GitHub API. |
| `GITHUB_API_RETRY_MAX_ATTEMPTS` | `3` | Retry attempts for transient GitHub API failures. |
| `GITHUB_API_RETRY_INITIAL_BACKOFF_MS` | `500` | Initial retry backoff. |
| `GITHUB_API_RETRY_BACKOFF_MULTIPLIER` | `2` | Backoff multiplier between retries. |

**AI Review integration** — optional, and disabled by default. Policy and Security analysis run normally without it.

| Variable | Default | Purpose |
|---|---|---|
| `AI_REVIEW_ENABLED` | `false` | Master switch for the AI Review Engine. |
| `ANTHROPIC_API_KEY` | empty | Anthropic API key. Required if `AI_REVIEW_ENABLED=true`. |
| `ANTHROPIC_API_BASE_URL` | `https://api.anthropic.com` | Anthropic API base URL. |
| `ANTHROPIC_API_VERSION` | `2023-06-01` | Anthropic API version header. |
| `ANTHROPIC_MODEL` | `claude-opus-4-6` | Model used for review generation. |
| `ANTHROPIC_PROMPT_VERSION` | `v1` | Recorded alongside each AI review run. |
| `ANTHROPIC_CONNECT_TIMEOUT_MS` | `5000` | Connect timeout for Anthropic calls. |
| `ANTHROPIC_READ_TIMEOUT_MS` | `30000` | Read timeout for Anthropic calls. |
| `ANTHROPIC_RETRY_MAX_ATTEMPTS` | `3` | Retry attempts on transient failures (429, 5xx, network errors). |
| `ANTHROPIC_RETRY_INITIAL_BACKOFF_MS` | `500` | Initial retry backoff. |
| `ANTHROPIC_RETRY_BACKOFF_MULTIPLIER` | `2` | Backoff multiplier between retries. |
| `AI_REVIEW_EXECUTION_CORE_POOL_SIZE` / `_MAX_POOL_SIZE` / `_QUEUE_CAPACITY` | `1` / `4` / `50` | Sizing for the AI review thread pool, kept smaller than the deterministic pipeline's own pool since LLM calls are slower per request. |

**Pipeline and report tuning** — optional; the defaults are reasonable for local use.

| Variable | Default | Purpose |
|---|---|---|
| `ANALYSIS_MAX_CHANGED_FILES` | `300` | Upper bound on changed files processed per pull request. |
| `ANALYSIS_EXECUTION_CORE_POOL_SIZE` / `_MAX_POOL_SIZE` / `_QUEUE_CAPACITY` | `2` / `8` / `50` | Sizing for the deterministic (Policy/Security) execution pool. |
| `REPORT_AI_WAIT_TIMEOUT_SECONDS` | `120` | How long report publication waits for AI Review before publishing without it. |
| `REPORT_SWEEP_INTERVAL_MS` | `60000` | How often the report timeout sweep runs. |

**Observability** — optional; the defaults work for local development. See [docs/Observability.md](docs/Observability.md) for what these control.

| Variable | Default | Purpose |
|---|---|---|
| `MANAGEMENT_SERVER_PORT` | `8081` | Port Actuator (health/info/metrics/prometheus/startup) is served on — separate from `SERVER_PORT`. |
| `DEPLOYMENT_ENVIRONMENT` | `local` | Free-text label surfaced at `/actuator/info` as `deployment.environment` (matched case-insensitively against `prod`/`stag`/`dev`/`local`). |
| `OBSERVABILITY_THRESHOLD_GITHUB_API_MS` | `3000` | Slow-call WARN threshold for GitHub API calls. |
| `OBSERVABILITY_THRESHOLD_POLICY_MS` | `1000` | Slow-call WARN threshold for Policy Engine evaluation. |
| `OBSERVABILITY_THRESHOLD_SECURITY_MS` | `1000` | Slow-call WARN threshold for Security Engine evaluation. |
| `OBSERVABILITY_THRESHOLD_REVIEW_MS` | `30000` | Slow-call WARN threshold for AI Review Engine evaluation. |
| `OBSERVABILITY_THRESHOLD_ANALYSIS_MS` | `5000` | Slow-call WARN threshold for the analysis pipeline. |

**Rate limiting** — optional; the defaults are reasonable for local use. See [docs/Security-Hardening.md](docs/Security-Hardening.md) for the full reference.

| Variable | Default | Purpose |
|---|---|---|
| `RATE_LIMIT_LOGIN_IP_CAPACITY` / `_REFILL_SECONDS` | `10` / `60` | Login attempts allowed per client IP per window. |
| `RATE_LIMIT_LOGIN_ACCOUNT_CAPACITY` / `_REFILL_SECONDS` | `5` / `60` | Login attempts allowed per account email per window. |
| `RATE_LIMIT_REFRESH_CAPACITY` / `_REFILL_SECONDS` | `10` / `60` | Refresh attempts allowed per client IP per window. |
| `RATE_LIMIT_WEBHOOK_CAPACITY` / `_REFILL_SECONDS` | `100` / `60` | GitHub webhook deliveries allowed per window (one global bucket). |
| `RATE_LIMIT_REPOSITORY_SYNC_CAPACITY` / `_REFILL_SECONDS` | `5` / `60` | Manual repository-sync requests allowed per user per window. |
| `RATE_LIMIT_CLEANUP_INTERVAL_MS` | `300000` | How often idle (fully-refilled) rate-limit buckets are evicted from memory. |

**Production**

Three startup checks exist only under the `prod` profile — see [Bootstrap Administrator](#bootstrap-administrator) for what they do. There is no separate variable to enable them; they activate automatically when `SPRING_PROFILES_ACTIVE=prod`.

## Bootstrap Administrator

On every startup, `BootstrapAdminInitializer` checks whether a user with email `BOOTSTRAP_ADMIN_EMAIL` already exists. If not, it creates one with the `ADMINISTRATOR` role, using `BOOTSTRAP_ADMIN_PASSWORD` hashed with the same password encoder used everywhere else in the application. This is what makes the platform reachable on a fresh database — without it, there'd be no way to create the first user, since user management itself is admin-only.

It only ever creates the account once. If the account already exists — including across restarts against the same database — changing `BOOTSTRAP_ADMIN_PASSWORD` afterward has no effect on it. There's no built-in password reset for this account beyond updating the row directly or using the regular user-management API once you're logged in as an administrator.

Three separate startup validators exist to stop an unrotated default from reaching a real deployment: `JwtSecretStartupValidator`, `GitHubSecretsStartupValidator`, and `BootstrapAdminStartupValidator`. Each is a `@Component` scoped to `@Profile("prod")`, running a `@PostConstruct` check that throws `IllegalStateException` if its corresponding value still matches the one committed in `application.yml`. They don't exist as beans at all under `local` or `dev` — under those profiles, the application boots regardless of what these values are set to.

As of Milestone 10: Security Hardening, each validator also rejects a value that *isn't* the exact default but still looks like a placeholder (contains `changeme`, `placeholder`, `password`, `admin`, `test`, `secret123`, or `default`, case-insensitively), and enforces a minimum length (`JWT_SECRET` 32 characters, `GITHUB_WEBHOOK_SECRET` 20, `BOOTSTRAP_ADMIN_PASSWORD` 12). See [docs/Security-Hardening.md](docs/Security-Hardening.md).

In practice, this means: local development works out of the box with every default in place, but starting the application with `SPRING_PROFILES_ACTIVE=prod` fails immediately, before the application is reachable, unless `JWT_SECRET`, `GITHUB_WEBHOOK_SECRET`, `GITHUB_APP_ID`, `BOOTSTRAP_ADMIN_PASSWORD`, and one of `GITHUB_APP_PRIVATE_KEY`/`GITHUB_APP_PRIVATE_KEY_PATH` have all been changed from their defaults.

The reason this matters for the bootstrap password specifically: it's a plaintext value committed to a public repository. Anyone who can read this codebase can read it. An administrator account — full access to every repository, every user, and every governance decision GateKeeper has recorded — should not be reachable with a password that ships in source control.

## Running the Application

**Backend** — from `backend/`:

```bash
./mvnw spring-boot:run
```

**Frontend** — from `frontend/`:

```bash
npm run dev
```

Once both are running:

| What | URL |
|---|---|
| Frontend (login page) | `http://localhost:5173/login` |
| Backend base URL | `http://localhost:8080` |
| Backend health check | `http://localhost:8081/actuator/health` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

Swagger UI and the OpenAPI JSON endpoint are available under `local` and `dev`. They're disabled under `prod` (`springdoc.swagger-ui.enabled: false` in `application-prod.yml`).

Note that the health check runs on port **8081**, not 8080 — Actuator (health, metrics, info, prometheus, startup) is served on its own management port, entirely separate from the API port. See [docs/Observability.md](docs/Observability.md) for the full reference and why.

Sign in with the bootstrap administrator credentials described above. The frontend redirects to `/dashboard` after a successful login.

## Running Tests

**Backend** — from `backend/`:

```bash
./mvnw test
```

Most tests are plain unit tests with no external dependencies. A subset are full integration tests, annotated `@Testcontainers`, that start a real, disposable PostgreSQL container per test class rather than mocking the database (as of Milestone 9: `AnalysisRunAndPolicyFindingQueryIntegrationTest`, `AuditLogIntegrationTest`, `InstallationOnboardingIntegrationTest`, `ObservabilityIntegrationTest`, `PolicyEngineExecutionIntegrationTest`, `PullRequestQueryIntegrationTest`, `PullRequestWebhookIntegrationTest`, `ReviewDecisionCheckRunIntegrationTest`, `ReviewDecisionIntegrationTest`, `SecurityEngineExecutionIntegrationTest`, `SecurityFindingQueryIntegrationTest`, `UserAndAuthLazyLoadingIntegrationTest` — search for `@Testcontainers` under `backend/src/test` for the current, authoritative list, since new milestones add to it). These need a Docker daemon that Testcontainers can actually detect from wherever `mvn test` is running. If it can't find one, those tests fail with `IllegalState: Could not find a valid Docker environment` while everything else still passes — that's an environment issue, not a sign the code is broken. Several of these tests also use WireMock to stand in for the GitHub API, so a real GitHub App is never required to run the suite.

**Frontend** — from `frontend/`:

```bash
npm run build
```

There is no automated frontend test suite (no Jest, Vitest, or Playwright configured in `package.json`). `npm run build` runs `tsc -b` followed by `vite build`, which at least catches type errors. `npm run lint` runs `oxlint` separately.

## Common Problems

**"Could not find a valid Docker environment" when running backend tests.** Testcontainers can't reach a Docker daemon from the test process. Confirm `docker info` works from the same shell/user context Maven runs in. This only affects the six integration tests listed above — the rest of the suite doesn't need Docker.

**Port 5433 already in use.** Something else on your machine is already bound to it (uncommon, but possible if you're running more than one project that made the same choice to avoid 5432). Set `DB_PORT` in `.env` (or export it before running `docker compose up`) to a free port, and update `DB_URL` to match if you're running the backend outside of `docker compose`.

**Database authentication fails after editing `.env`.** As noted in [Database Setup](#database-setup), PostgreSQL only applies its user/password on first initialization of an empty data directory. Changing `.env` after the `postgres-data` volume already exists won't change the running database's credentials. Run `docker compose down -v` to remove the volume and start over, or update the credentials inside the running database directly.

**Flyway refuses to run.** If you're pointing the application at a PostgreSQL database that already has tables in it from something other than this project, Flyway may still fail even with `baseline-on-migrate` set, depending on what's already there. The simplest fix for local development is to start from an empty database.

**`mvn clean` fails to delete `target/gatekeeper-core.jar`.** A previous `java -jar target/gatekeeper-core.jar` process is likely still running and holding the file open — this shows up most often on Windows, where a running executable can't be overwritten. Stop the process (`Get-Process -Name java` in PowerShell, then `Stop-Process`) and retry.

**GitHub App fields are unset, and nothing happens when a pull request is opened.** This is expected, not a bug. Without `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, and `GITHUB_WEBHOOK_SECRET` configured, there's no way for GitHub to reach the webhook endpoint with a signature the backend can verify, and no way for the backend to authenticate to the GitHub API to fetch pull request data. Repository management, authentication, and every other part of the platform work normally without it.

**AI findings never show up on a report.** Check `AI_REVIEW_ENABLED` — it defaults to `false`. With it off, the pipeline runs Policy and Security only, and each published report shows an AI status of `DISABLED` rather than an error. This is the documented default, not a failure.

## Project Documentation

| Document | Purpose |
|---|---|
| [docs/Product-Vision.md](docs/Product-Vision.md) | Problem statement, target users, and MVP scope |
| [docs/Architecture.md](docs/Architecture.md) | System layers, module boundaries, and architecture decisions |
| [docs/Domain-Model.md](docs/Domain-Model.md) | Core business entities and how they relate |
| [docs/Database.md](docs/Database.md) | Data model and entity relationships |
| [docs/API-Design.md](docs/API-Design.md) | REST API conventions and endpoint groups |
| [docs/Observability.md](docs/Observability.md) | Health endpoints, metrics, structured logging, and the management port |
| [docs/Security-Hardening.md](docs/Security-Hardening.md) | HTTP security headers, rate limiting, JWT hardening, secrets validation, dependency/secret scanning, Docker hardening |
| [docs/Product-Backlog.md](docs/Product-Backlog.md) | Epics, features, and the sprint plan the MVP was built against |

## Development Notes

The frontend is not containerized. `docker-compose.yml` has a `frontend` service block, but it's commented out; the only way to run the frontend today is `npm run dev` (or a static `npm run build` output served by something else you set up yourself).

The backend runs independently of the frontend. You can exercise the entire API through Swagger UI or `curl` without the frontend running at all, and the frontend just needs some backend reachable at `VITE_API_BASE_URL` — it doesn't have to be running on the same machine.

GitHub App and Anthropic credentials are both optional for local development. Everything except real webhook ingestion and AI-generated findings works without them, including manually created repositories, authentication, user management, and browsing any data already in the database.
