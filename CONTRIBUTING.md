# Contributing to GateKeeper

Thanks for your interest in contributing. This document covers how the project is put together, what's expected of a change before it's opened as a pull request, and where to go for more context.

## Before You Start

Read these first:

- [README.md](README.md) — what GateKeeper does and how a pull request moves through the pipeline.
- [INSTALLATION.md](INSTALLATION.md) — how to get the backend and frontend running locally.
- [docs/Architecture.md](docs/Architecture.md) — the module boundaries and design decisions behind the current structure.

The architecture document matters more than it might seem. GateKeeper's engines (Policy, Security, AI Review) are deliberately isolated from each other and from the Verdict Engine — AI Review in particular has no code path into the merge decision. Understanding why that separation exists will save you from proposing a change that looks reasonable in isolation but breaks a boundary the rest of the system depends on.

## Development Setup

Follow [INSTALLATION.md](INSTALLATION.md) for setup — starting PostgreSQL, running the backend and frontend, environment variables, and the bootstrap administrator account. It's the canonical source for this; instructions aren't duplicated here.

## Repository Structure

```
backend/                 Spring Boot API (Java 21, Maven)
frontend/                React + TypeScript SPA (Vite)
docs/                    Product vision, architecture, domain model, and API design documents
docker-compose.yml       PostgreSQL, and an optional containerized backend
infrastructure/          Reserved for infrastructure-as-code; currently empty
scripts/                 Reserved for operational scripts; currently empty
```

The backend and frontend are independent applications that happen to share a repository — see [INSTALLATION.md](INSTALLATION.md#repository-setup) for the full breakdown.

## Coding Standards

These are the conventions already in use across the codebase. Follow the pattern of the module you're touching rather than introducing a new one.

**Backend package structure.** The backend is organized by feature, not by layer — `analysisrun`, `policy`, `security`, `user`, `role`, and so on each own their entity, repository, service, and controller in one package, with a `dto` subpackage for request/response types (see `backend/src/main/java/com/gatekeeper/analysisrun/`). There's no `controllers/`, `services/`, `repositories/` split across the whole application.

**Repository pattern.** Spring Data JPA repositories extend `JpaRepository`. Where a query needs to eagerly fetch a lazy association — `open-in-view` is disabled, so the Hibernate session closes when the transactional service method returns — the repository method is overridden with `@EntityGraph(attributePaths = {...})` rather than reworking the entity's fetch type. `UserRepository.findAll()` and `AnalysisRunRepository.findWithPullRequestAndRepositoryById(...)` are existing examples. Custom filtering uses JPA Specifications (see `AnalysisRunSpecifications`), not hand-written JPQL.

**DTO separation.** Request and response DTOs are separate types from the JPA entities and live in each module's `dto` package. Modules built from Sprint 3 onward compose the response DTO inside the `@Transactional` service method, before the session closes — this is the pattern to follow for new code. A few earlier modules (`user`, `role`) still convert to DTOs in the controller layer instead; that's a known inconsistency from earlier in the project, not a pattern to copy.

**Services.** One service per feature package, constructor-injected, holding the `@Transactional` boundaries. Cross-module coordination (e.g., fanning out to Policy, Security, and AI Review for a single Analysis Run) lives in `orchestration`, not inside any single feature's service.

**Startup validators.** Configuration that must never reach production with a default value (`JWT_SECRET`, GitHub webhook/App secrets, the bootstrap administrator password) is enforced by a `@Component @Profile("prod")` class with a `@PostConstruct` check that throws `IllegalStateException` — see `JwtSecretStartupValidator`, `GitHubSecretsStartupValidator`, and `BootstrapAdminStartupValidator`. If you're adding a new secret-backed setting, this is the established way to guard it.

**Frontend structure.** `frontend/src` is organized by concern: `pages/` (route-level components), `components/`, `services/` (one file per backend resource, e.g. `userService.ts`), `types/` (TypeScript interfaces matching backend DTOs), `hooks/`, `layouts/`, and `routes/`. There's no state management library beyond React's own hooks and a small `useAuth` hook for the authenticated user.

**Frontend conventions.** No modal/dialog library is used anywhere in the app — forms that need to appear inline (see `UsersPage.tsx`) are built as conditionally rendered sections, not portal-based modals. API error handling follows the pattern in `LoginPage.tsx`: a `describeError()` helper extracts the message from the backend's `ApiErrorResponse` shape rather than showing a generic failure message.

## Before Opening a Pull Request

- The backend builds: `./mvnw clean package` (from `backend/`).
- Backend tests pass: `./mvnw test`. Some tests require a Docker daemon reachable by Testcontainers — see [INSTALLATION.md](INSTALLATION.md#running-tests) if you don't have one available; note that in the pull request rather than silently skipping the check.
- The frontend builds: `npm run build` (from `frontend/`). This runs a TypeScript check as part of the build, which is currently the only automated frontend verification — there is no frontend test suite yet.
- Documentation is updated if the change affects it — README.md, INSTALLATION.md, or the relevant document under `docs/`, depending on what changed.
- The change doesn't include unrelated edits — formatting sweeps, renames, or reordering of code you didn't otherwise need to touch.

There's no configured formatter or static analysis step in the backend build (no Checkstyle, Spotless, or PMD in `backend/pom.xml`). The frontend has `oxlint` (`npm run lint`) configured in `frontend/.oxlintrc.json`, but it isn't wired into `npm run build` or enforced anywhere — run it if you're touching frontend code, but don't treat a clean build as implying a clean lint pass, and don't invent a stricter requirement than what's actually configured.

## Commit Guidelines

There's no enforced commit message convention (no Conventional Commits, no required prefixes). Keep commits small and focused — one logical change per commit, with a message that explains what changed and, where it isn't obvious, why. A commit that mixes an unrelated refactor with a bug fix is harder to review and harder to revert later.

## Pull Requests

- Keep each pull request focused on one change. A pull request that fixes a bug and also restructures a nearby module makes both parts harder to review.
- Explain why the change is needed, not just what it does — link to an issue if one exists.
- Include screenshots for anything that changes the UI.
- Describe the testing you actually performed: which automated tests you ran, and whether you verified the change manually (and how, if the change isn't covered by an automated test).

## Reporting Bugs

Open an issue with:

- Steps to reproduce.
- Expected behavior.
- Actual behavior.
- Relevant logs or stack traces (backend logs, browser console output, or both, depending on where the bug shows up).
- Environment: OS, Java version, Node version, and whether you're running the backend via `spring-boot:run`, the packaged jar, or `docker compose`.

## Feature Requests

GateKeeper's MVP scope is defined in `docs/Product-Vision.md`, and its module boundaries are defined in `docs/Architecture.md`. Before proposing a feature, check both — a request that conflicts with the architecture's separation of deterministic and AI-advisory findings, for example, is unlikely to be accepted as proposed. Feature requests that align with the project's stated direction and Future Scope are the ones most likely to move forward.

## Code of Conduct

There is no separate `CODE_OF_CONDUCT.md` in this repository yet. Be respectful and constructive in issues, pull requests, and reviews.

## License

GateKeeper is licensed under the MIT License (see [LICENSE](LICENSE)). By submitting a contribution, you agree that it will be licensed under the same terms.
