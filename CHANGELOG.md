# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-07-24

### Added

- **Authentication & RBAC** — JWT-based login with access and refresh tokens, and role-based access control across six roles (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer).
- **Repository Management** — connecting, listing, and removing repositories tracked by GateKeeper.
- **Repository Onboarding** — "Connect GitHub" install flow, installation reconciliation directly against GitHub's API (works even in environments where GitHub's webhook cannot reach the backend, such as local development), automatic pruning of installation records GitHub no longer recognizes.
- **Pull Request Analysis** — GitHub App webhook integration that creates an Analysis Run for every pull request commit, tracked through its own lifecycle.
- **Policy Engine** — deterministic checks for engineering policy violations, with findings classified by severity and category, and organization-manageable rule configuration.
- **Security Engine** — deterministic checks for common security issues, feeding directly into the governance decision alongside Policy findings.
- **AI Review Engine** — advisory code review backed by Anthropic's API, running independently of the deterministic engines and with graceful degradation (retries, fallback to a report without AI findings) when the provider is unavailable.
- **Verdict Engine** — aggregates Policy and Security findings into a single Approved/Blocked decision per pull request, structurally excluding AI Review findings from that decision.
- **Unified Engineering Reports** — one published report per Analysis Run, combining Policy, Security, and AI findings with the Verdict.
- **GitHub Checks** — the automated verdict and the human reviewer decision are each published as their own GitHub Check Run.
- **Reviewer Decision Workflow** — a human reviewer's decision, recorded and published back to GitHub independently of the automated verdict.
- **Governance Dashboard (Insights)** — organization-wide view of findings by severity and category, verdict outcomes, block rate, and report status.
- **Repository Governance** — the same governance metrics broken down per repository, for comparing enforcement consistency across repositories.
- **User Management** — administrator-only user and role management: create, edit, disable, and remove users.
- **Structured Audit Logging** — every governance-relevant action recorded as a structured event, filterable and exportable.
- **Observability** — Actuator and Micrometer metrics, correlation IDs, structured JSON logging, and a dedicated management port isolated from the public API.
- **Redesigned frontend** — Inbox (home), Pull Requests, Security triage, Repositories, Policies, Insights, and Settings (Access/Audit): six top-level destinations, replacing fifteen routes. One narrative page per pull request (verdict → rationale → evidence → decision → history) replacing four separate detail pages. Dark-first design system with a light theme, skeleton loading states, and a curated demo dataset (`SPRING_PROFILES_ACTIVE=local,demo`) for evaluating the product without a live GitHub connection.
- **Local developer experience** — one-command `start-dev`/`stop-dev` scripts (PowerShell, bash, batch) covering Docker, Postgres, backend, and frontend, with port-conflict detection, safe reuse of already-running instances, and `.env` as a single persistent configuration source for both the native and docker-compose run paths.

### Security

- JWT-based authentication for all API access, with stateless session handling.
- Refresh token rotation: each refresh token is single-use and revoked on redemption, with server-side tracking by hash rather than raw value, and reuse detection (a revoked-but-reused token is logged and counted separately from an ordinarily-expired one).
- JWT issuer validation and bounded clock-skew tolerance.
- GitHub webhook signature verification (HMAC-SHA256 over the raw request body, constant-time comparison), with defensive sanitization of the configured secret (byte-order mark and whitespace stripped before use).
- HTTP security headers (Content-Security-Policy, HSTS, Referrer-Policy, Permissions-Policy) and CORS applied via plain servlet filters registered ahead of Spring Security's own chain — the more reliable pattern this project settled on after Spring Security's `.headers()`/`.cors()` DSL configuration proved not to reliably apply on a live server in this environment.
- Rate limiting (Bucket4j): login attempts limited independently by IP and by account (closing both the distributed-attack and one-IP-many-accounts bypass vectors), plus limits on refresh, webhook delivery, and repository sync.
- Production startup validators that refuse to start the application under the `prod` profile if the JWT signing secret, GitHub webhook secret, GitHub App credentials, or bootstrap administrator password are still at their committed development defaults or otherwise fail a minimum-strength check (blocklisted placeholder words, minimum length).
- GitHub App configuration diagnostics at every startup (not just `prod`): reports exactly which required variable is missing, reports webhook-secret length (never the value), and refuses to start if a real App ID is configured against a webhook secret still at its placeholder default.
- Bootstrap administrator protection: the seeded administrator account is created once per database and never has its password reset automatically, with the production startup validator above as an additional safeguard.
- Explicit request/header/multipart size limits; dependency scanning (OWASP Dependency Check, CycloneDX SBOM, npm audit, Gitleaks) integrated into CI; non-root Docker container with `no-new-privileges`.

### Fixed

- CORS preflight regression introduced during security hardening.
- Security triage queue showing findings from superseded analysis runs or non-open pull requests as if they represented an active, unaddressed critical issue — now defaults to a pull request's current, open, latest-run findings only, with an explicit toggle to review historical/superseded ones.
- "Connect GitHub" reporting the App as unconfigured despite a working GitHub App, caused by an undocumented required variable (`GITHUB_APP_SLUG`) — now documented in three places and surfaced by startup diagnostics if missing.
- A stale GitHub installation record surviving an App reinstall, so "Manage" pointed at an installation ID GitHub no longer recognized — installation reconciliation now runs synchronously on every "Connect GitHub" completion and prunes records GitHub no longer recognizes.

### Documentation

- Added README.md, describing the project, its pipeline, and how to get started.
- Added INSTALLATION.md, a detailed setup guide covering the database, backend, frontend, environment variables, and common problems.
- Added CONTRIBUTING.md, describing the contribution workflow and coding conventions.
- Added SECURITY.md, describing supported versions, vulnerability reporting, and current security features and limitations.
- Added the architecture and design documents under `docs/` (Product-Vision, Architecture, Domain-Model, Database, API-Design, Product-Backlog, Authorization-Model, Decisions, Observability, Policy-Development, Security-Hardening, Development).

### Known limitations

- No automated frontend test suite (TypeScript type-checking runs as part of the production build).
- Frontend is not containerized; runs via `npm run dev` or a static build, not through `docker-compose.yml`.
- GitHub App and Anthropic credentials are optional for local development — without them, webhook ingestion and AI Review have nothing to connect to, but the rest of the platform is unaffected.
- Rate limiting is in-memory and per-instance; a documented Redis-backed upgrade path exists for multi-instance deployments but is not built.

### Notes

This is the first public release of GateKeeper, representing the completed MVP as scoped in `docs/Product-Vision.md`. Further work will be tracked as subsequent releases rather than added retroactively to this one.

[1.0.0]: https://github.com/arcinth/gatekeeper-core/releases/tag/v1.0.0
