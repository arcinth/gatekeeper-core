# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-07-16

### Added

- **Authentication & RBAC** — JWT-based login with access and refresh tokens, and role-based access control across six roles (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer).
- **Repository Management** — connecting, listing, and removing repositories tracked by GateKeeper.
- **Pull Request Analysis** — GitHub App webhook integration that creates an Analysis Run for every pull request commit, tracked through its own lifecycle.
- **Policy Engine** — deterministic checks for engineering policy violations, with findings classified by severity and category.
- **Security Engine** — deterministic checks for common security issues, feeding directly into the governance decision alongside Policy findings.
- **AI Review Engine** — advisory code review backed by Anthropic's API, running independently of the deterministic engines and with graceful degradation (retries, fallback to a report without AI findings) when the provider is unavailable.
- **Verdict Engine** — aggregates Policy and Security findings into a single Approved/Blocked decision per pull request, structurally excluding AI Review findings from that decision.
- **Unified Engineering Reports** — one published report per Analysis Run, combining Policy, Security, and AI findings with the Verdict.
- **Governance Dashboard** — organization-wide view of findings by severity and category, verdict outcomes, block rate, and report status.
- **Repository Governance** — the same governance metrics broken down per repository, for comparing enforcement consistency across repositories.
- **User Management** — administrator-only user and role management: create, edit, disable, and remove users.

### Security

- JWT-based authentication for all API access, with stateless session handling.
- Refresh token rotation: each refresh token is single-use and revoked on redemption, with server-side tracking by hash rather than raw value.
- GitHub webhook signature verification (HMAC-SHA256 over the raw request body, constant-time comparison).
- Production startup validators that refuse to start the application under the `prod` profile if the JWT signing secret, GitHub webhook secret, GitHub App credentials, or bootstrap administrator password are still at their committed development defaults.
- Bootstrap administrator protection: the seeded administrator account is created once per database and never has its password reset automatically, with the production startup validator above as an additional safeguard.

### Documentation

- Added README.md, describing the project, its pipeline, and how to get started.
- Added INSTALLATION.md, a detailed setup guide covering the database, backend, frontend, environment variables, and common problems.
- Added CONTRIBUTING.md, describing the contribution workflow and coding conventions.
- Added SECURITY.md, describing supported versions, vulnerability reporting, and current security features and limitations.
- Added the architecture and design documents under `docs/` (Product-Vision, Architecture, Domain-Model, Database, API-Design, Product-Backlog).

### Notes

This is the first public release of GateKeeper, representing the completed MVP as scoped in `docs/Product-Vision.md`. Further work will be tracked as subsequent releases rather than added retroactively to this one.

[1.0.0]: https://github.com/arcinth/gatekeeper-core/releases/tag/v1.0.0
