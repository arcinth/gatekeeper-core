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
```

Policy and Security findings are deterministic and feed the Verdict. AI Review findings are advisory and only ever reach the Engineering Report — the Verdict Engine has no code path that can read them.

## Features

**Authentication & Access Control**
- JWT login with access and refresh tokens
- Role-based permissions enforced on every endpoint (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer)

**Repository Integration**
- GitHub App webhook integration for pull request events
- Repository connection, listing, and removal

**Analysis Pipeline**
- Every pull request commit is tracked as an Analysis Run with its own lifecycle (received, queued, in progress, completed, failed)
- Policy, Security, and AI Review engines run independently against the same pull request snapshot

**Policy Engine**
- Deterministic checks for engineering policy violations (for example, TODO/FIXME comments left in code)
- Findings are classified by severity and category

**Security Engine**
- Deterministic checks for common security issues (for example, hardcoded secrets, insecure cryptography usage)
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

**Governance Dashboard**
- Organization-wide view of findings by severity and category, verdict outcomes, block rate, and report status

**Repository Governance**
- The same governance metrics broken down per repository, for comparing enforcement consistency across repositories

**User Management**
- Admin-only user and role management: create, edit, disable, and remove users

## Tech stack

**Backend:** Java 21, Spring Boot 3.3.5, Spring Security (JWT), Spring Data JPA, PostgreSQL 16, Flyway, Maven

**Frontend:** React 19, TypeScript, Vite, Tailwind CSS, React Router, Axios

## Project layout

```
backend/                 Spring Boot API (Java 21)
frontend/                React + TypeScript SPA
docs/                    Product vision, architecture, domain model, and API design documents
docker-compose.yml       Postgres and the backend, for local development
```

## Getting started

Prerequisites: JDK 21, Node.js 20 or later, Docker.

Start Postgres:

```bash
docker compose up -d postgres
```

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`, with a default administrator account at `admin@gatekeeper.local` / `ChangeMe123!`. Change this password before running under the `prod` Spring profile — startup fails on purpose if the default is still in place.

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

The dashboard is available at `http://localhost:5173` and talks to the backend at `http://localhost:8080/api/v1` by default.

This is enough to sign in and explore the dashboard against an empty database. A full setup walkthrough, including GitHub App registration, will live in `INSTALLATION.md`.

## Configuration

The backend reads its configuration from `backend/src/main/resources/application.yml`, with environment variables overriding the defaults. The values above are enough to boot the application and use the dashboard. Two integrations need their own credentials before they do anything:

- **GitHub App** (`GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`) — required to receive real pull request webhooks.
- **Anthropic API** (`ANTHROPIC_API_KEY`, `AI_REVIEW_ENABLED=true`) — required for the AI Review Engine. AI Review is disabled by default; Policy and Security run without it.

See `application.yml` for the complete list of supported variables.

## Running tests

Backend:

```bash
cd backend
./mvnw test
```

Most tests run without any external dependencies. Some integration tests use Testcontainers and require a working Docker daemon.

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
| [docs/Product-Backlog.md](docs/Product-Backlog.md) | Epics, features, and the sprint plan the MVP was built against |

## Project status

v1.0.0 is the completed MVP described in `docs/Product-Vision.md`: repository management, authentication and RBAC, the Policy/Security/AI Review pipeline, the Verdict Engine, Engineering Reports, the governance dashboard, per-repository governance, and user management.

Further work — additional analysis engines, more source control integrations, and the other items listed under Future Scope in `docs/Product-Vision.md` — will be tracked as separate releases rather than added to the MVP itself.

## Known limitations

- The frontend has no automated test suite yet.
- The frontend is not containerized; it runs via `npm run dev` or a static build, not through `docker-compose.yml`.
- GitHub App and Anthropic integration require their own configuration. Without it, webhook ingestion and AI Review have nothing to connect to — the rest of the platform is unaffected.

## License

MIT — see [LICENSE](LICENSE).
