# GateKeeper — Security Hardening

**Version:** 1.0
**Status:** Approved
**Document Owner:** GateKeeper Team

---

## Purpose

This document describes GateKeeper's production security hardening (Milestone 10): HTTP security headers, rate limiting, JWT hardening, input hardening, secrets validation, dependency/secret scanning, and Docker hardening. It is aimed at whoever operates or reviews a deployment — for the business-facing REST API see [API-Design.md](./API-Design.md), and for health/metrics/logging see [Observability.md](./Observability.md).

Every control here wraps the existing platform rather than replacing it. Authentication, RBAC, the JWT token format, and every existing API contract are unchanged. Nothing in this milestone required a database migration.

---

## 1. HTTP Security Headers

Set by `SecurityHeadersFilter`, a plain servlet filter (`response.setHeader(...)` before `filterChain.doFilter(...)`), the same pattern `CorrelationIdFilter` (Milestone 7) already uses. This was a deliberate implementation choice, not the first approach tried: registering these headers through `HttpSecurity.headers(...)`'s `HeadersConfigurer`/`HeaderWriterFilter` DSL was implemented first and passed `@WebMvcTest`, but was proven - via live verification against a real running instance, not just the test slice - to never actually reach the wire, despite the writer list being correctly populated (confirmed by reflecting on the built `SecurityFilterChain`). The plain-filter approach was verified working end-to-end and is what ships. See `SecurityHeadersFilter`'s own Javadoc for the full account.

| Header | Value | Rationale |
|---|---|---|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | GateKeeper's backend is a JSON API - it never serves HTML except Swagger UI (`local`/`dev` only), so a maximally restrictive policy is correct. The frontend (a separately-hosted SPA) owns its own CSP independently. |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | Instructs browsers to only ever reach GateKeeper over HTTPS once they've seen one HTTPS response, for a year. |
| `Referrer-Policy` | `no-referrer` | No legitimate reason for a request originating from GateKeeper's API responses to leak the referring URL. |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` | An explicit deny-all for browser features GateKeeper has no use for. |
| `X-Content-Type-Options` | `nosniff` | Spring Security's own default - left implicit rather than re-declared. |
| `X-Frame-Options` | `DENY` | Spring Security's own default - left implicit rather than re-declared. |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` | Also a Spring Security default, applied to **every** response platform-wide - already satisfies "never cache a token-bearing response" for `/api/v1/auth/**` without any extra code. An explicit rule scoped to just the auth endpoints was tried and removed: Spring's own default header writer runs first and claims the header, so a scoped writer added afterward never took effect. Confirmed via `AuthControllerTest`. |

**Deliberately not added:** `Cross-Origin-Opener-Policy` and `Cross-Origin-Embedder-Policy`. GateKeeper never opens or is opened by cross-origin windows and never embeds cross-origin resources requiring COEP opt-in - both headers defend against a threat model this platform doesn't have, and adding them would be unnecessary complexity.

---

## 2. Rate Limiting

Token-bucket rate limiting via [Bucket4j](https://github.com/bucket4j/bucket4j), entirely in-memory, behind a small abstraction so controllers never depend on Bucket4j directly.

**Architecture** (`com.gatekeeper.security.ratelimit`):

- `RateLimiter` - the only interface application code depends on (`tryConsume(key, rule)`).
- `InMemoryRateLimiter` - the only implementation today. A future Redis-backed implementation, for horizontal scaling across multiple instances, would implement this same interface; no caller would change.
- `RateLimitRule` - a plain value type (capacity, refill tokens, refill period) with no Bucket4j types in its signature.
- `RateLimitService` - the only class that knows each named limit's configured shape; controllers call `checkLogin`/`checkRefresh`/`checkWebhook`/`checkRepositorySync` and never see `RateLimiter` or Bucket4j at all.

**Login uses two independent buckets, not one combined key**: an IP bucket and an account (email) bucket. A combined key would let an attacker distributing the same credential-stuffing attempt across many IPs bypass a per-IP limit (each IP+email pair looks "new"), and would let one IP attacking many accounts bypass a per-account limit the same way. Exceeding *either* bucket rejects the request.

| Limit | Key | Default capacity | Default refill period | Env vars |
|---|---|---|---|---|
| Login (IP) | client IP | 10 | 60s | `RATE_LIMIT_LOGIN_IP_CAPACITY`, `RATE_LIMIT_LOGIN_IP_REFILL_SECONDS` |
| Login (account) | email, case-insensitive | 5 | 60s | `RATE_LIMIT_LOGIN_ACCOUNT_CAPACITY`, `RATE_LIMIT_LOGIN_ACCOUNT_REFILL_SECONDS` |
| Refresh | client IP | 10 | 60s | `RATE_LIMIT_REFRESH_CAPACITY`, `RATE_LIMIT_REFRESH_REFILL_SECONDS` |
| GitHub webhook | none - one global bucket | 100 | 60s | `RATE_LIMIT_WEBHOOK_CAPACITY`, `RATE_LIMIT_WEBHOOK_REFILL_SECONDS` |
| Repository sync | authenticated user id | 5 | 60s | `RATE_LIMIT_REPOSITORY_SYNC_CAPACITY`, `RATE_LIMIT_REPOSITORY_SYNC_REFILL_SECONDS` |

Each is a single-window bucket (capacity refills entirely once per period) rather than a two-tier burst+sustained shape - a deliberate simplification for this milestone; Bucket4j supports multiple bandwidths per bucket if a future need justifies the added complexity.

**Webhook ordering.** `GitHubWebhookController` checks the rate limit *before* signature verification, so a flood of forged deliveries is rejected before paying the HMAC computation cost.

**Failure response.** Every limit throws `RateLimitExceededException` (an `ApiException`), handled by `GlobalExceptionHandler` exactly like every other handled exception: `429 Too Many Requests` via the existing `ApiErrorResponse` envelope, logged at `WARN`, and counted twice - `gatekeeper.errors.total{category="rate_limit", error_code="GK-429"}` (the general error-monitoring counter every handler already contributes to) and `gatekeeper.rate_limit.exceeded{endpoint}` (a dedicated counter distinguishing *which* limit tripped: `auth.login.ip`, `auth.login.account`, `auth.refresh.ip`, `github.webhook`, `github.repository-sync` - a fixed, small tag set, never a raw path).

**Memory management.** `InMemoryRateLimiter` never removes a bucket that is still partially or fully consumed, but periodically evicts buckets that have fully refilled back to capacity (`gatekeeper.rate-limit.cleanup-interval-ms`, default every 5 minutes) - without this, a flood of distinct keys (e.g. an attacker cycling through random login emails) would grow the bucket map without bound, turning the rate limiter itself into a memory-exhaustion vector.

**Horizontal scaling.** This implementation is per-instance: a 3-instance deployment effectively multiplies each limit by up to 3 in the worst case. This is a known, accepted limitation for this milestone - see Remaining Limitations below.

**Client IP source.** Rate limiting reads `HttpServletRequest.getRemoteAddr()` directly (the TCP peer), not an `X-Forwarded-For` header - trusting a client-supplied header for rate-limit keying would let an attacker set an arbitrary value and bypass the limit entirely. If GateKeeper is deployed behind a reverse proxy, enable Spring's `server.forward-headers-strategy: framework` so `getRemoteAddr()` reflects the proxy-forwarded address correctly (Spring's own `ForwardedHeaderFilter`, not custom header parsing here).

---

## 3. JWT Hardening

| Area | What changed |
|---|---|
| **Issuer validation** | `JwtService.parseClaims` now calls `.requireIssuer(issuer)` - a token whose `iss` claim doesn't match this instance's own configured issuer is rejected. Every token this service itself mints already carries the right value, so this only ever rejects a token that came from somewhere else. |
| **Clock skew** | `.setAllowedClockSkewSeconds(clockSkewSeconds)` (default 30, `JWT_CLOCK_SKEW_SECONDS`) gives a small, bounded tolerance for drift between application instances/NTP - the same kind of allowance `GitHubAppAuthService` already applies to GitHub's own App JWTs. |
| **Refresh-token reuse detection** | `AuthService.refresh` already rotates the refresh token on every use (revokes the presented token, issues a new one) - that predates this milestone. What's new: presenting an *already-revoked* token (not just a nonexistent one) is now distinguished from an ordinary invalid/expired token - logged at `WARN` and counted (`gatekeeper.security.refresh_token_reuse`), since reuse of a revoked token is the textbook signal of a stolen-and-replayed token. The caller still just sees "invalid or expired" either way - this doesn't tell an attacker which case they hit. |
| **Invalid JWT logging** | `JwtAuthenticationFilter`'s previously-silent broad `catch (Exception)` is replaced with three specific, logged branches (`malformed_or_expired`, `wrong_type`, `user_not_found`). Previously, an invalid/expired/tampered access token on a normal API call produced no log line and no metric at all. |
| **Invalid JWT metrics** | `gatekeeper.auth.invalid_token{reason}` - `reason` is one of the three fixed values above, never free text. |

**Preserved unchanged:** stateless access tokens (still self-contained JWTs, still no server-side revocation - a stolen access token remains valid until its natural 15-minute expiry; this is an accepted tradeoff of a short TTL, not a gap this milestone closes), refresh token rotation's existing mechanics, and the JWT claim shape/format itself.

---

## 4. Input Hardening

| Control | Value | Where |
|---|---|---|
| Max HTTP request header size | 8KB | `server.tomcat.max-http-request-header-size` |
| Max request body Tomcat will read-then-discard on error | 2MB | `server.tomcat.max-swallow-size` |
| Max form-post body size | 2MB | `server.tomcat.max-http-form-post-size` |
| Max multipart file size | 1MB | `spring.servlet.multipart.max-file-size` |
| Max multipart request size | 1MB | `spring.servlet.multipart.max-request-size` |

Every value above matches Tomcat/Spring Boot's own current default - made explicit so each is a reviewed, documented decision rather than an implicit default that could silently change on a future framework upgrade. No endpoint in this codebase accepts a file upload today; the multipart limits are a deliberate ceiling so a future multipart endpoint inherits a reviewed limit by default instead of an unbounded one.

**Validation.** Every request DTO sampled during this milestone's review already used Jakarta Bean Validation consistently (`@NotBlank`, `@Email`, `@Size`, etc.) - no gaps were found requiring new annotations.

---

## 5. Secrets Management

Extends the three existing `@Profile("prod")` startup validators (`JwtSecretStartupValidator`, `GitHubSecretsStartupValidator`, `BootstrapAdminStartupValidator`) with two new, shared checks (`SecretStrengthValidator`, a plain static utility - not a Spring bean):

- **`requireNotWeak`** - rejects a value containing any of a small set of common placeholder substrings, case-insensitively: `changeme`, `change-me`, `placeholder`, `password`, `admin`, `test`, `secret123`, `default`. Applied on top of each validator's existing exact-default-value comparison, so a value that isn't the *exact* committed default but is still an obvious placeholder (e.g. `MyAdminPassword2026`) is also rejected.
- **`requireMinimumLength`** - a floor appropriate to what the secret is used for:

| Secret | Minimum length | Why |
|---|---|---|
| `JWT_SECRET` | 32 characters | HS256 requires a key of at least 256 bits (32 bytes); `Keys.hmacShaKeyFor` already enforces this at bean-construction time in *every* profile, but this makes it an explicit, prod-specific policy rather than an incidental side effect of that library check. |
| `GITHUB_WEBHOOK_SECRET` | 20 characters | An HMAC-SHA256 key; a practical floor, not a cryptographic one. |
| `BOOTSTRAP_ADMIN_PASSWORD` | 12 characters | A real login credential granting full administrative access; 12 is a common enterprise minimum-length floor. |

Both checks run only under `@Profile("prod")`, exactly like the existing checks - `local`/`dev` are unaffected, and every default value shipped in `application.yml` continues to work out of the box for local development.

No cloud-specific secret manager (Vault, AWS Secrets Manager, GCP Secret Manager) is introduced - every secret remains a plain environment variable or mounted file, keeping GateKeeper deployable on any infrastructure.

**Secret rotation.** Rotating `JWT_SECRET` invalidates every currently-issued access and refresh token instantly (a new signing key can't validate tokens signed with the old one) - plan a maintenance window. Rotating `GITHUB_WEBHOOK_SECRET` or `BOOTSTRAP_ADMIN_PASSWORD` has no such blast radius; update the environment variable and restart.

---

## 6. Dependency & Secret Scanning (CI)

Three checks run in CI, none in the default local build loop (so a normal `mvn package`/`npm run build` never pays their cost):

| Check | Tool | Workflow | Severity policy |
|---|---|---|---|
| Backend dependency vulnerabilities | [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/) (`dependency-check-maven`) | `.github/workflows/backend.yml` | Fails the build on CVSS ≥ 7 (High/Critical) |
| Backend SBOM | [CycloneDX](https://cyclonedx.org/) (`cyclonedx-maven-plugin`) | `.github/workflows/backend.yml` | Informational - uploaded as a build artifact (`bom.json`), not a gate |
| Frontend dependency vulnerabilities | `npm audit` | `.github/workflows/frontend.yml` | `--audit-level=high`, matching the backend's CVSS ≥ 7 policy |
| Secret scanning | [Gitleaks](https://github.com/gitleaks/gitleaks) | `.github/workflows/security.yml` | Any finding fails the job |

Gitleaks runs via its official Docker image directly (`zricethezav/gitleaks:latest`) rather than a third-party GitHub Action - Gitleaks itself is open source (MIT) and free to run this way for any repository, avoiding a separate Action-specific license requirement some wrapper Actions impose for organization use. It scans full commit history (`fetch-depth: 0`), not just the current commit, since a secret committed once and later removed is still a leaked secret.

**Recording an accepted-risk suppression.** If Dependency-Check ever flags a finding that's a false positive or an accepted risk (e.g. a transitive dependency with no available fix and no exploitable path in how GateKeeper uses it), record it in a `dependency-check-suppression.xml` file referenced from the plugin configuration in `pom.xml`, with a comment explaining the reasoning - do not silently lower the severity threshold to make a build pass.

---

## 7. Docker Hardening

| Control | Change |
|---|---|
| Non-root container | `backend/Dockerfile` creates a dedicated system user/group (`gatekeeper`, no login shell, no home directory) and switches to it via `USER gatekeeper` before `ENTRYPOINT`. The base image ran as root by default; any future code-execution vulnerability in the app or a dependency is now contained by a non-root user rather than starting from root. |
| `no-new-privileges` | `docker-compose.yml`'s backend service sets `security_opt: [no-new-privileges:true]` - blocks a process inside the container from gaining privileges beyond what it started with (e.g. via a setuid binary), closing the one escalation path a non-root user alone doesn't. |

**Preserved unchanged:** the multi-stage build, base images (`maven:3.9-eclipse-temurin-21` build stage, `eclipse-temurin:21-jre-jammy` runtime), and the Milestone 9 `HEALTHCHECK` targeting the liveness probe.

**Not implemented this milestone** (documented, not built - see Remaining Limitations): a read-only root filesystem (`--read-only` + `--tmpfs /tmp`) and an explicit `--cap-drop=ALL`. Both are safe, standard hardening steps for this image, but enforcing them in the committed `docker-compose.yml` without first verifying every runtime write path (temp files, log buffering, etc.) risked breaking the container in a way this milestone's time didn't allow fully re-verifying end to end; recommended as deployment-level (orchestrator/compose override) configuration instead.

---

## 8. Configuration Security Cleanup

`SecurityConfig` no longer has a `permitAll()` rule for `/actuator/health` - Milestone 9 already moved every Actuator endpoint onto a separate management port with its own `ManagementSecurityConfig`, and the main API port hasn't served `/actuator/*` at all since then (confirmed live: a `NoResourceFoundException`, handled as a 404 by `GlobalExceptionHandler`). The rule was dead configuration describing a trust boundary that no longer existed - the same category of cleanup Milestone 9 itself performed for the pre-Actuator dead config in `application.yml`.

CORS's `allowedHeaders` was narrowed from `"*"` to the specific headers GateKeeper's own frontend actually sends (`Authorization`, `Content-Type`, `X-Correlation-Id`). `allowedOrigins` was already a specific list (never a wildcard), so this wasn't exploitable before - it's defense-in-depth, an explicit allowlist being stronger practice than a wildcard even where currently safe.

**CORS enforcement itself moved out of `HttpSecurity.cors(...)`.** That was the original implementation and, like the header customizers described in Section 1, it built a correctly-configured `CorsFilter` (confirmed via reflection on the built `SecurityFilterChain` - right origins, methods, headers, credentials flag) that nonetheless rejected every real cross-origin preflight with `403` on live verification, regardless of configuration correctness - the same class of failure as Section 1's headers, and likely the same underlying cause. CORS is now handled by a plain `CorsFilter` (Spring's own class, not a custom reimplementation) registered directly via `FilterRegistrationBean` at `Ordered.HIGHEST_PRECEDENCE`, entirely outside Spring Security's filter chain - see `SecurityConfig.corsFilterRegistration()`. This has the added benefit of resolving preflight requests (which never carry credentials) before they reach Spring Security's authorization logic at all, so a preflight to a protected endpoint no longer depends on that endpoint's auth rules.

---

## 9. Security Event Monitoring

Extends Milestone 9's existing MeterRegistry/structured-logging infrastructure - no second monitoring system.

| Event | Metric | Log |
|---|---|---|
| Failed login | `gatekeeper.auth.login.attempts{outcome="failure"}` (Milestone 9) | - |
| Invalid JWT on an API call | `gatekeeper.auth.invalid_token{reason}` | INFO (routine, not an anomaly) |
| Refresh-token reuse | `gatekeeper.security.refresh_token_reuse` | WARN |
| Rate limit exceeded | `gatekeeper.rate_limit.exceeded{endpoint}` and `gatekeeper.errors.total{category="rate_limit"}` | WARN |
| Webhook signature failure | `gatekeeper.webhook.signature.failures` (Milestone 9) | - |
| Permission denied | `gatekeeper.errors.total{category="authorization"}` (Milestone 9) | INFO |

Operational logs (SLF4J via the Milestone 9 MDC/logback pipeline) and metrics (Micrometer) are used throughout, per the existing Milestone 9 distinction from audit logs (`AuditLogService`, reserved for business-meaningful, attributable actions). No new audit-log event types were added this milestone - login/logout aren't in `AuditLogService`'s event catalog, and adding them is a Milestone 7-adjacent feature change outside this milestone's hardening scope.

---

## 10. Release Security Checklist

- [x] OWASP Top 10 reviewed (see the Milestone 10 design proposal's threat model)
- [x] JWT hardened (issuer validation, clock skew, reuse detection, invalid-token logging/metrics)
- [x] Rate limiting enabled (login IP+account, refresh, webhook, repository sync)
- [x] Security headers enabled (CSP, HSTS, Referrer-Policy, Permissions-Policy, plus Spring Security's own defaults)
- [x] Secrets validated (weak-pattern + minimum-length checks, `prod` profile only)
- [x] Dependency scanning enabled (OWASP Dependency-Check, `npm audit`)
- [x] SBOM generated (CycloneDX)
- [x] Secret scanning enabled (Gitleaks)
- [x] Non-root container
- [x] Docker hardened (non-root user, `no-new-privileges`)
- [x] Production configuration validated (startup validators fail fast under `prod` on any unrotated/weak secret)

Re-run this checklist before every production release.

---

## Remaining Limitations

- **Rate limiting is per-instance, not distributed.** A multi-instance deployment effectively multiplies each limit by the instance count in the worst case. A Redis-backed `RateLimiter` implementation (same interface, see Section 2) is the documented upgrade path, not built this milestone.
- **Access tokens have no revocation mechanism.** A stolen access token remains valid until its natural 15-minute expiry. This is an accepted tradeoff of a short-lived, stateless token design, not a gap this milestone closes - building revocation would mean reintroducing server-side state for access tokens, a genuine authentication redesign outside this milestone's scope.
- **Read-only filesystem / capability dropping are documented, not enforced in the committed Docker config.** See Section 7.
- **Refresh-token reuse detection logs and counts, but does not automatically revoke the rest of that user's sessions.** Full "revoke the whole token family" behavior would require a schema change (a family/lineage column) and carries a false-positive risk (e.g. a client retry racing a legitimate rotation) - flagged as a documented future enhancement rather than built now.

---

## Related Documents

- [Observability.md](./Observability.md) - health endpoints, metrics, structured logging, the management port.
- [Architecture.md](./Architecture.md) - system layers and architecture decisions, including ADR-009 (security hardening is cross-cutting, not a new layer).
- [API-Design.md](./API-Design.md) - the business-facing `/api/v1` REST API, including the `429` status code this milestone adds.
- [../INSTALLATION.md](../INSTALLATION.md) - environment variables, including rate-limit thresholds and JWT clock-skew tolerance documented here.
