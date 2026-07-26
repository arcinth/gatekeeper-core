# GateKeeper

Deterministic policy and security governance for GitHub pull requests, with AI-assisted review kept strictly advisory.

GateKeeper analyzes every pull request against configurable engineering policy and security rules and publishes a single governance verdict — approved or blocked — back to GitHub as a Check Run. An AI-assisted review runs alongside the deterministic checks and is included in the report, but it has no influence over the verdict itself. The backend is a Spring Boot application; the frontend is a React single-page app. Both are covered by a CI pipeline that runs dependency and secret scanning on every push.

---

## Overview

Pull request review usually mixes two different kinds of judgment: checks that should produce the same result every time a team can audit and version (a secret left in a diff, a policy violation, a missing test), and checks that require actual judgment. Tooling built around large language models tends to blur that line — a model's suggestion can end up treated as ground truth for a merge decision without anyone deciding that should happen.

GateKeeper keeps the two kinds of checks structurally separate rather than relying on convention to keep them apart. A Policy Engine and a Security Engine run deterministic, rule-based checks; their findings feed a Verdict Engine that produces the Approved/Blocked decision. An AI Review Engine, backed by Anthropic's API, runs independently and its findings are published in the same Engineering Report — but the Verdict Engine has no code path that can read them. If the AI provider is slow, rate-limited, or unavailable, the deterministic verdict is unaffected.

The rest of the system follows from that split: every pull request commit is tracked as its own Analysis Run, the engines run against the same snapshot of the diff, and the result — verdict, findings, and report — is what gets published back to GitHub and shown in the dashboard.

---

## Features

**Authentication**
- JWT authentication with access and refresh tokens, refresh-token rotation, and reuse detection
- Role-based access control enforced on every endpoint, across six roles (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer)

**Repository Management**
- Repository registration through a GitHub App install flow, with automatic installation reconciliation against GitHub's own API
- Governance dashboard (Insights): findings by severity and category, verdict outcomes, and block rate, both organization-wide and per repository
- Historical record of past Analysis Runs and their reports per pull request

**Pull Request Review**
- Policy Engine: deterministic checks for engineering policy violations, with severity/category classification and per-organization rule configuration
- Security Engine: deterministic checks for common issues such as hardcoded secrets, AWS access keys, GitHub PATs, insecure cryptography, and insecure randomness
- AI-assisted review, advisory only: backed by Anthropic's API, included in the Engineering Report, and structurally excluded from the Verdict Engine's decision
- GitHub Check Runs: the automated verdict and the human reviewer's decision are each published as their own Check Run

**Developer Experience**
- REST API covering authentication, repositories, analysis runs, findings, verdicts, and administration
- Swagger UI and an OpenAPI document, generated from the API itself (springdoc)
- Docker Compose for local PostgreSQL, with an optional containerized backend
- CI/CD via GitHub Actions: separate backend, frontend, and security-scanning workflows
- Automated testing: JUnit and Testcontainers-backed integration tests on the backend, TypeScript type-checking on the frontend

---

## Architecture

The backend is a single Spring Boot application organized by feature — `analysisrun`, `policy`, `security`, `user`, and so on — each owning its own entity, repository, service, and controller, rather than a cross-cutting `controllers/`/`services/`/`repositories/` split. Coordinating a single Analysis Run across the Policy, Security, and AI Review engines is handled by a dedicated `orchestration` package, not by any one feature's service.

The frontend is an independent React SPA that talks to the backend only through its REST API. The two applications share a repository but not a runtime.

*Architecture diagram — to be added.*

See [docs/Architecture.md](docs/Architecture.md) for module boundaries and the reasoning behind them, including why the Verdict Engine is structurally unable to read AI Review findings.

---

## Screenshots

*Screenshots of the dashboard, a pull request's Engineering Report, and the security triage queue will be added here.*

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.13, Spring Security, Spring Data JPA, Maven |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, React Router |
| Database | PostgreSQL 16, Flyway (schema migrations) |
| Infrastructure | Docker, Docker Compose, GitHub Actions |
| Testing | JUnit 5, Mockito, Testcontainers, WireMock (backend); TypeScript compiler checks (frontend) |
| CI/CD | GitHub Actions, OWASP Dependency-Check, Gitleaks, npm audit |

---

## Project Structure

```
backend/          Spring Boot API (Java 21)
frontend/         React + TypeScript SPA
docs/             Architecture, domain model, API design, and development guides
scripts/          Local development scripts (start/stop, demo dataset)
secrets/          Local-only credential material — gitignored
docker-compose.yml
```

---

## Getting Started

### Prerequisites

- JDK 21
- Node.js 22 or later
- Docker

### Installation

```bash
git clone https://github.com/arcinth/gatekeeper-core.git
cd gatekeeper-core
```

### Running locally

The fastest path starts PostgreSQL, the backend, and the frontend together, reusing anything already running:

```bash
./scripts/start-dev.sh          # Linux/macOS
.\scripts\start-dev.ps1         # Windows PowerShell
scripts\start-dev.bat           # Windows, double-click or cmd
npm run dev:all                 # any platform, via npm
```

Stop everything the same way, with `scripts/stop-dev.sh` / `.ps1` / `.bat`, or `npm run dev:stop`.

To run each part by hand instead:

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

The frontend runs at `http://localhost:5173` and talks to the backend at `http://localhost:8080/api/v1`. Sign in with the default administrator account, `admin@gatekeeper.local` / `ChangeMe123!` — change this before running under the `prod` Spring profile; startup fails on purpose if the default is still in place.

### Running with Docker

`docker-compose.yml` also defines a `backend` service:

```bash
docker compose up -d
```

This runs PostgreSQL and the backend as containers. The frontend is not containerized yet — run it as shown above.

### Useful commands

```bash
npm run dev:all:demo                 # start everything and seed a curated demo dataset
(cd backend && ./mvnw test)          # backend tests
(cd frontend && npm run build)       # frontend build + type-check
(cd frontend && npm run lint)        # frontend lint (oxlint)
```

Full setup, including GitHub App registration and every supported environment variable, is in [INSTALLATION.md](INSTALLATION.md).

---

## Testing

Backend tests are plain JUnit 5/Mockito unit tests plus a set of integration tests, under `com.gatekeeper.integration`, that use Testcontainers to start a real, disposable PostgreSQL container per test class rather than mocking the database. Several of these also use WireMock to stand in for the GitHub API. Integration tests require a Docker daemon Testcontainers can detect.

The frontend has no dedicated automated test suite yet; `npm run build` runs a TypeScript type-check as part of the production build.

Three GitHub Actions workflows run on every push:

- **Backend CI** — build, run the full backend test suite, then OWASP Dependency-Check (fails on any dependency with a CVSS score of 7 or higher) and a CycloneDX SBOM.
- **Frontend CI** — build and `npm audit --audit-level=high`.
- **Security Scanning** — Gitleaks, run against the full commit history rather than just the current commit.

---

## Security

- **JWT authentication** — stateless access and refresh tokens, refresh-token rotation on every use, and detection of a revoked-but-reused token.
- **Role-based access control** — every endpoint is authorized against the caller's role, enforced on the backend regardless of what the frontend shows.
- **Secret management** — startup validators refuse to start the application under the `prod` profile if the JWT secret, GitHub webhook secret, GitHub App credentials, or bootstrap administrator password are still at their committed development defaults.
- **Dependency scanning** — OWASP Dependency-Check and npm audit run in CI on every push; Gitleaks scans the full commit history for secrets.

See [SECURITY.md](SECURITY.md) for the complete picture, including current limitations, and how to report a vulnerability.

---

## Documentation

| Document | Purpose |
|---|---|
| [INSTALLATION.md](INSTALLATION.md) | Full setup: environment variables, GitHub App registration, running tests |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution workflow and coding conventions |
| [docs/Architecture.md](docs/Architecture.md) | System layers, module boundaries, and architecture decisions |
| [docs/Product-Vision.md](docs/Product-Vision.md) | Problem statement, target users, and MVP scope |
| [docs/Domain-Model.md](docs/Domain-Model.md) | Core business entities and how they relate |
| [docs/Database.md](docs/Database.md) | Data model and entity relationships |
| [docs/API-Design.md](docs/API-Design.md) | REST API conventions and endpoint groups |
| [docs/Authorization-Model.md](docs/Authorization-Model.md) | Roles, permissions, and where each is enforced |
| [docs/Security-Hardening.md](docs/Security-Hardening.md) | Rate limiting, JWT hardening, secrets management |
| [docs/Observability.md](docs/Observability.md) | Metrics, structured logging, correlation IDs |
| [docs/Policy-Development.md](docs/Policy-Development.md) | How to add a new Policy or Security rule |
| [docs/Development.md](docs/Development.md) | Day-to-day local development workflow |
| [CHANGELOG.md](CHANGELOG.md) | What shipped, by release |
| [SECURITY.md](SECURITY.md) | Security features, limitations, and vulnerability reporting |

---

## Future Work

- An automated frontend test suite — the frontend currently relies on TypeScript type-checking alone.
- A Redis-backed rate limiter for multi-instance deployments; the current implementation is in-memory and per instance.
- Additional source control integrations beyond GitHub (GitLab, Bitbucket, Azure DevOps).
- Additional deterministic analysis engines alongside Policy and Security.

---

## License

MIT — see [LICENSE](LICENSE).
