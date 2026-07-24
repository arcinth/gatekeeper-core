# GateKeeper

**Status:** Stable
**Version:** v1.0.0
**Development status:** MVP complete

GateKeeper is an engineering governance platform for pull request analysis. It combines deterministic policy and security analysis with AI-assisted review to produce a single engineering report for every pull request.

Deterministic engines decide whether a pull request passes governance. AI review is advisory only and never influences that decision.

## How a pull request moves through the system

```
Pull Request opened / updated
        │
        ▼
  GitHub webhook received
        │
        ▼
  Analysis Run created
        │
        ▼
  Policy Engine · Security Engine · AI Review Engine
  (run independently against the same PR snapshot)
        │
        ▼
  Verdict Engine  →  Approved / Blocked
  (Policy + Security findings only)
        │
        ▼
  Unified Engineering Report
  (Policy + Security + AI findings, plus the Verdict)
        │
        ▼
  GitHub Check Runs published
  (automated verdict + human reviewer decision, separately)
```

Policy and Security findings are deterministic and feed the Verdict. AI Review findings are advisory and only ever reach the Engineering Report — the Verdict Engine has no code path that can read them.

## Features

**Authentication & Access Control**
- JWT login with access and refresh tokens (rotation, reuse detection)
- Role-based permissions enforced on every endpoint (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer)

**Repository Integration**
- GitHub App webhook integration for pull request, installation, and installation-repositories events
- "Connect GitHub" install flow with automatic installation reconciliation — works even where GitHub's webhook can't reach the backend (e.g. local development), and prunes installation records GitHub no longer recognizes
- Repository connection, listing, and removal

**Analysis Pipeline**
- Every pull request commit is tracked as an Analysis Run with its own lifecycle (received, queued, in progress, completed, failed)
- Policy, Security, and AI Review engines run independently against the same pull request snapshot

**Policy Engine**
- Deterministic checks for engineering policy violations (for example, TODO/FIXME comments left in code)
- Findings are classified by severity and category; rules are configurable per organization

**Security Engine**
- Deterministic checks for common security issues (hardcoded secrets, AWS access keys, GitHub PATs, insecure cryptography, insecure randomness)
- Findings feed directly into the governance decision, the same as Policy findings

**AI Review**
- Advisory code review backed by Anthropic's API
- Never participates in the merge decision, and the pipeline keeps working (with retries and graceful degradation) if the AI provider is unavailable

**Verdict Engine**
- Aggregates Policy and Security findings into a single Approved/Blocked decision
- Structurally excludes AI findings from that decision

**Engineering Reports**
- One published report per Analysis Run, combining Policy, Security, and AI findings with the Verdict
- Publication waits for AI Review to finish, or times out, so the AI section is never half-populated

**GitHub Checks**
- The automated verdict and the human reviewer decision are each published as their own GitHub Check Run

**Reviewer Decisions**
- A human reviewer's own decision, recorded and published back to GitHub independently of the automated verdict

**Governance Dashboard (Insights)**
- Organization-wide view of findings by severity and category, verdict outcomes, block rate, and pipeline health

**Repository Governance**
- The same governance metrics broken down per repository, for comparing enforcement consistency across repositories

**Security Triage**
- A queue of open security findings, worst first, scoped by default to what's still actionable (a pull request's current, latest-run findings, not superseded or historical ones)

**Audit Logging**
- Structured audit events for governance-relevant actions, filterable and exportable

**Observability**
- Actuator + Micrometer metrics, correlation IDs, structured JSON logging, on a management port isolated from the public API

**User Management**
- Admin-only user and role management: create, edit, disable, and remove users

## Tech stack

**Backend:** Java 21, Spring Boot 3.3.5, Spring Security (JWT), Spring Data JPA, PostgreSQL 16, Flyway, Maven

**Frontend:** React 19, TypeScript, Vite, Tailwind CSS, React Router, Axios

## Project layout

```
backend/                 Spring Boot API (Java 21)
frontend/                React + TypeScript SPA
docs/                    Product vision, architecture, domain model, API design, and dev workflow documents
scripts/                 Cross-platform dispatcher behind `npm run dev:all` / `dev:stop`
secrets/                 Local-only credential material (GitHub App private key) - gitignored
docker-compose.yml       Postgres (and, optionally, a containerized backend) for local development
start-dev.ps1/.sh/.bat   One-command local dev startup - see docs/Development.md
stop-dev.ps1/.sh/.bat    Matching shutdown
```

## Getting started

Prerequisites: JDK 21, Node.js 20 or later, Docker.

The fastest path — one command starts Postgres, the backend, and the frontend together, detecting and reusing anything already running:

```bash
./start-dev.sh          # Linux/macOS
.\start-dev.ps1         # Windows PowerShell
start-dev.bat           # Windows, double-click or cmd
npm run dev:all         # any platform, if you'd rather remember one npm script
```

Add `-Demo` / `--demo` / `npm run dev:all:demo` to also seed a curated demo dataset instead of starting against an empty database. Stop everything the same way, with `stop-dev.ps1`/`.sh`/`.bat` or `npm run dev:stop`. See [docs/Development.md](docs/Development.md) for what these scripts do, port-conflict handling, and troubleshooting.

Prefer to run each piece by hand instead:

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

The API is available at `http://localhost:8080`, with a default administrator account at `admin@gatekeeper.local` / `ChangeMe123!`. Change this password before running under the `prod` Spring profile — startup fails on purpose if the default is still in place. The frontend is available at `http://localhost:5173` and talks to the backend at `http://localhost:8080/api/v1` by default.

This is enough to sign in and explore the product against an empty database (or the curated demo dataset, with `-Demo`). For a full setup walkthrough, including GitHub App registration and every supported environment variable, see [INSTALLATION.md](INSTALLATION.md).

## Configuration

The backend reads its configuration from `backend/src/main/resources/application.yml`, with environment variables overriding the defaults. Local development additionally reads a root-level `.env` file (copy `.env.example` to `.env`), which `start-dev`/`stop-dev` load into the backend process automatically — this is the one place to set credentials once instead of re-exporting them in every new terminal. The values shipped by default are enough to boot the application and use the product. Two integrations need their own credentials before they do anything:

- **GitHub App** (`GITHUB_APP_ID`, `GITHUB_APP_SLUG`, `GITHUB_APP_PRIVATE_KEY` or `GITHUB_APP_PRIVATE_KEY_PATH`, `GITHUB_WEBHOOK_SECRET`) — required to receive real pull request webhooks and to make "Connect GitHub" produce a working install link. A startup diagnostic (`GitHubAppConfigurationDiagnostics`) reports exactly which of these is missing, on every profile.
- **Anthropic API** (`ANTHROPIC_API_KEY`, `AI_REVIEW_ENABLED=true`) — required for the AI Review Engine. AI Review is disabled by default; Policy and Security run without it.

See `INSTALLATION.md` and `application.yml` for the complete list of supported variables.

## Running tests

Backend:

```bash
cd backend
./mvnw test
```

Most tests run without any external dependencies. Integration tests under `com.gatekeeper.integration` use Testcontainers and require a Docker environment Testcontainers can detect (this works reliably in CI and on Linux/macOS; some native-Windows Docker Desktop configurations are unreliable for Testcontainers specifically, even though `docker compose` itself works fine there).

Frontend:

```bash
cd frontend
npm run build
```

There is no automated frontend test suite yet; `npm run build` runs a TypeScript type-check as part of the production build.

## Documentation

| Document | Purpose |
|---|---|
| [docs/Product-Vision.md](docs/Product-Vision.md) | Problem statement, target users, and MVP scope |
| [docs/Architecture.md](docs/Architecture.md) | System layers, module boundaries, and architecture decisions |
| [docs/Domain-Model.md](docs/Domain-Model.md) | Core business entities and how they relate |
| [docs/Database.md](docs/Database.md) | Data model and entity relationships |
| [docs/API-Design.md](docs/API-Design.md) | REST API conventions and endpoint groups |
| [docs/Authorization-Model.md](docs/Authorization-Model.md) | Roles, permissions, and where each is enforced |
| [docs/Security-Hardening.md](docs/Security-Hardening.md) | Rate limiting, JWT hardening, secrets management, and the header/CORS filter pattern |
| [docs/Observability.md](docs/Observability.md) | Metrics, structured logging, correlation IDs, the management port |
| [docs/Policy-Development.md](docs/Policy-Development.md) | How to add a new Policy or Security rule |
| [docs/Development.md](docs/Development.md) | Day-to-day local dev: one-command start/stop, port conflicts, troubleshooting |
| [docs/Testing-Checklist.md](docs/Testing-Checklist.md) | Release-validation checklist: automated gates and manual QA |
| [docs/Migration-Guide.md](docs/Migration-Guide.md) | What changed for existing checkouts, and how to cut the v1.0.0 release |
| [docs/Decisions.md](docs/Decisions.md) | Architecture decision records |
| [docs/Product-Backlog.md](docs/Product-Backlog.md) | Epics, features, and the sprint plan the MVP was built against |
| [CHANGELOG.md](CHANGELOG.md) | What shipped, by release |
| [SECURITY.md](SECURITY.md) | Security features, known limitations, and how to report a vulnerability |

## Project status

v1.0.0 is the completed MVP described in `docs/Product-Vision.md`: repository onboarding, authentication and RBAC, the Policy/Security/AI Review pipeline, the Verdict Engine, Engineering Reports, GitHub Checks, audit logging, observability, production security hardening, and a redesigned frontend built around one narrative page per pull request.

Further work — additional analysis engines, more source control integrations, and the other items listed under Future Scope in `docs/Product-Vision.md` — will be tracked as separate releases rather than added to the MVP itself.

## Known limitations

- The frontend has no automated test suite yet.
- The frontend is not containerized; it runs via `npm run dev` or a static build, not through `docker-compose.yml`.
- GitHub App and Anthropic integration require their own configuration. Without it, webhook ingestion and AI Review have nothing to connect to — the rest of the platform is unaffected.
- Rate limiting is in-memory and per instance; a documented Redis-backed upgrade path exists for multi-instance deployments but isn't built.

## License

MIT — see [LICENSE](LICENSE).
