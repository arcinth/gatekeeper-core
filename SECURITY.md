# Security Policy

This document describes how security issues in GateKeeper are reported, what security-relevant behavior the platform currently implements, and what its known limitations are. It's written against the actual codebase, not an aspirational one — where something isn't in place yet, it's listed as a limitation rather than described as if it exists.

## Supported Versions

v1.0.0 is the current release and the only one receiving security fixes.

## Reporting a Vulnerability

Do not open a public GitHub issue for a suspected security vulnerability. Public issues are visible to everyone, including before a fix is available.

A dedicated security contact (email address or private disclosure channel) has not yet been established for this project. Until one is, report suspected vulnerabilities by contacting the maintainers directly through GitHub (for example, by opening a private security advisory on the repository, if enabled, or reaching a maintainer directly) rather than through a public issue.

When reporting, include:

- Steps to reproduce the issue.
- The impact — what an attacker could do with it.
- The affected version or commit.
- Relevant logs, if you have them and they don't themselves contain sensitive data.

## Security Features

**JWT authentication.** Login (`POST /api/v1/auth/login`) is handled by Spring Security's `DaoAuthenticationProvider` against BCrypt-hashed passwords. On success, the backend issues a signed JWT access token (HMAC-SHA256, via `JwtService`) carrying the user's ID, email, role, and organization. Every other endpoint requires this token in the `Authorization: Bearer` header; the API is fully stateless (`SessionCreationPolicy.STATELESS` — no server-side session).

**Refresh tokens.** Refresh tokens are separate JWTs with their own claim type, issued alongside the access token. Each one is tracked server-side in the `refresh_tokens` table by the SHA-256 hash of its JWT ID (`TokenHasher`), not the raw token. Using a refresh token revokes it and issues a new pair (`AuthService.refresh`) — a stored token can't be redeemed twice. Logout revokes the presented refresh token immediately.

**Role-based authorization.** Endpoints are secured with Spring Security method security (`@PreAuthorize`) checked against the role encoded in the JWT. `UserController` and `RoleController`, for example, require `ROLE_ADMINISTRATOR` on every endpoint. Six roles exist (Administrator, Developer, Technical Lead, Engineering Manager, Platform Engineer, DevSecOps Engineer); enforcement happens on the backend, not the frontend.

**Password hashing.** User passwords, including the bootstrap administrator's, are hashed with BCrypt (`PasswordEncoder` bean in `SecurityConfig`) before storage. Plaintext passwords are never persisted.

**GitHub webhook signature verification.** Inbound webhook requests are verified against the `X-Hub-Signature-256` header using HMAC-SHA256 over the raw request body (`WebhookSignatureVerifier`), with a constant-time comparison (`MessageDigest.isEqual`) to avoid leaking signature information through timing. Requests with a missing, malformed, or mismatched signature are rejected before any payload processing happens.

**Startup validators.** Three configuration values that are dangerous if left at their committed development defaults are checked at startup under the `prod` Spring profile: `JwtSecretStartupValidator` (JWT signing secret), `GitHubSecretsStartupValidator` (webhook secret, GitHub App ID, and private key), and `BootstrapAdminStartupValidator` (bootstrap administrator password). Each throws `IllegalStateException` during startup if its value still matches the known default, preventing the application from starting at all rather than starting in an insecure state. None of these beans exist under the `local` or `dev` profiles — they have no effect outside `prod`.

**Bootstrap administrator protection.** `BootstrapAdminInitializer` creates a single administrator account on first startup against an empty database, so there's a way to log in and create further users. It never resets the account's password on subsequent restarts, and — under `prod` — `BootstrapAdminStartupValidator` refuses to start the application at all if the password is still the committed default.

**AI Review isolation from governance.** The AI Review Engine has no code path into the Verdict Engine. The verdict (Approved/Blocked) is computed from Policy and Security findings only; AI Review findings reach the Engineering Report but cannot influence whether a pull request passes governance. This is a structural property of the pipeline (see `docs/Architecture.md`), not a configuration flag.

**Production profile behavior.** Under `prod`: Swagger UI and the OpenAPI JSON endpoint are disabled (`springdoc.swagger-ui.enabled: false`), SQL statement logging is off, and log verbosity drops to `WARN` at the root level. Combined with the startup validators above, a `prod` deployment either starts with all default secrets rotated, or doesn't start.

**CSRF.** CSRF protection is disabled (`SecurityConfig`). This is standard for a stateless, header-token-authenticated API with no server-side session or cookie-based authentication — CSRF exists to protect cookie-authenticated requests a browser sends automatically, which doesn't apply here since the frontend attaches the bearer token explicitly.

**CORS.** Allowed origins are configured via `CORS_ALLOWED_ORIGINS` (`gatekeeper.cors.allowed-origins`), defaulting to `http://localhost:5173` for local development. `allowCredentials` is enabled, so this value should be set explicitly — not left wide open — in any deployment.

## Production Deployment Notes

Before running GateKeeper with `SPRING_PROFILES_ACTIVE=prod`, the following must be set, or the application will fail to start:

- `BOOTSTRAP_ADMIN_PASSWORD` — must differ from the committed default (`ChangeMe123!`).
- `JWT_SECRET` — must differ from the committed default development value.
- `GITHUB_WEBHOOK_SECRET`, `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY` — `GitHubSecretsStartupValidator` requires all three to be present and non-default under `prod`, even if you don't plan to use GitHub integration immediately.

Two values have no dedicated startup check and should still be set deliberately:

- `ANTHROPIC_API_KEY` — required only if `AI_REVIEW_ENABLED=true`. There is no startup validator for this key; an invalid or missing key surfaces as AI Review failures at runtime (handled gracefully — see README.md), not a startup failure.
- `CORS_ALLOWED_ORIGINS` — should be set to the actual origin(s) serving the frontend, not left at the local-development default.

Swagger UI and the OpenAPI JSON endpoint are unavailable under `prod` by design (`springdoc.swagger-ui.enabled: false` in `application-prod.yml`).

## Known Security Limitations

These are current, verified limitations — not hypothetical concerns:

- Access and refresh tokens are stored in the browser's `localStorage` (`frontend/src/services/tokenStorage.ts`), which is readable by any JavaScript running on the page. An XSS vulnerability elsewhere in the frontend would be able to read both tokens.
- There is no login rate limiting. The `/api/v1/auth/**` endpoints accept requests without any throttling, so repeated login attempts against a known email are not slowed down or blocked by the backend.
- There is no account lockout after repeated failed login attempts.
- Frontend role gating (which admin pages and actions a user sees) is a UI convenience only. All real enforcement happens on the backend via `@PreAuthorize`; the frontend does not need to be trusted for authorization to hold.

## Third-party Services

**GitHub App.** GateKeeper integrates with GitHub through a GitHub App (`GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`) to receive pull request webhooks and call the GitHub API. The private key is used to generate short-lived installation tokens; it is never sent to GitHub and should be handled with the same care as any other private key. Webhook authenticity is verified as described above.

**Anthropic.** The AI Review Engine calls Anthropic's API (`ANTHROPIC_API_KEY`) when `AI_REVIEW_ENABLED=true`. Pull request diffs are sent to Anthropic as part of generating a review; if that data-sharing implication matters for your deployment, keep AI Review disabled (the default) or review Anthropic's own data handling terms before enabling it. GateKeeper does not modify or filter what's sent beyond what's needed to construct the review request.

**Docker.** Used for local development (PostgreSQL via `docker-compose.yml`) and for backend integration tests (Testcontainers, which starts a disposable PostgreSQL container per test class). The repository does not currently define a hardened or minimal-base-image production Docker deployment beyond the `backend/Dockerfile` used for local `docker compose` runs.

**PostgreSQL.** The system of record for all application data — users, repositories, analysis runs, findings, and reports. Database credentials are supplied via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); there is no encryption-at-rest or connection-level TLS configuration built into the application itself — that's a deployment-environment concern.

## Dependency Updates

Keep backend (Maven, `backend/pom.xml`) and frontend (npm, `frontend/package.json`) dependencies current, particularly Spring Security, Spring Boot, and the JWT library (`io.jsonwebtoken`). The project does not currently have automated dependency vulnerability scanning (no Dependabot configuration, no Snyk or equivalent) configured in the repository.

## License

GateKeeper is licensed under the MIT License (see [LICENSE](LICENSE)).
