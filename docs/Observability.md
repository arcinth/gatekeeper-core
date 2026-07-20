# GateKeeper — Observability

**Version:** 1.0
**Status:** Approved
**Document Owner:** GateKeeper Team

---

## Purpose

This document describes GateKeeper's operational observability (Milestone 9): how to check whether a running instance is healthy, what metrics it exposes, how its logs are structured, and how its performance and errors are monitored. It is aimed at whoever operates a deployment — not at API consumers (see [API-Design.md](./API-Design.md) for the business-facing REST API).

Observability in GateKeeper is entirely additive. It wraps the existing architecture (Auth, RBAC, GitHub Integration, Repository Onboarding, Policy Engine, Security Engine, Review Engine, Audit Logging) without changing any of it — no existing endpoint, response contract, or business behavior was altered to build this. It is built entirely on Spring Boot's own standard stack — Actuator, Micrometer, and a Prometheus registry — rather than a custom framework.

---

## 1. The Management Port: GateKeeper's Trust Model for Observability

Every Actuator endpoint (health, info, metrics, prometheus, startup) is served on a **separate HTTP port** (`management.server.port`, default `8081`, configurable via `MANAGEMENT_SERVER_PORT`) — entirely distinct from the public API port (`server.port`, default `8080`).

This is the same trust boundary GateKeeper already relies on for PostgreSQL: nothing on the management port requires a JWT or any application-level credential. Security is **network isolation** — the port is simply not reachable from outside the private network a real deployment runs in — not an authentication check. `docker-compose.yml` publishes it to the host for local development convenience only; a production deployment must not publish this port on a public load balancer or ingress.

This design was deliberately chosen over the alternative of introducing a new `SYSTEM_MONITOR` RBAC permission: Prometheus and other scraping tools have no natural way to hold a GateKeeper JWT, and a permission that exists solely to be handed to infrastructure tooling would be a permission in name only. Network isolation is the same posture already accepted for the database's own port.

**Why not just rely on the default?** Spring Boot's own `ManagementWebSecurityAutoConfiguration` applies a partial security scheme automatically the moment `management.server.port` differs from `server.port` — by default, only `/actuator/health` is open; everything else (info, metrics, prometheus, startup) returns 401. GateKeeper explicitly overrides this via `ManagementSecurityConfig` (a `@ManagementContextConfiguration(ManagementContextType.CHILD)` bean, registered through `META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports`) that permits all requests on the management port — matching the intended "isolation is the boundary" design rather than Spring Boot's own default of a half-open port.

**Endpoint allowlist.** `management.endpoints.web.exposure.include` is an explicit list: `health, info, metrics, prometheus, startup`. `env`, `beans`, `configprops`, `mappings`, `heapdump`, `threaddump`, and `shutdown` are never included, in any profile — defense in depth on top of network isolation, so even a misconfigured network boundary doesn't leak configuration, a full bean graph, a heap dump, or a remote shutdown switch.

---

## 2. Health Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Full health report — aggregates every indicator below. |
| `GET /actuator/health/readiness` | "Can this instance serve real traffic right now?" — includes the database. |
| `GET /actuator/health/liveness` | "Is the process itself still healthy?" — deliberately independent of the database and GitHub. |

**Indicators included:** Application (Spring Boot's own `livenessState`/`readinessState`), Database (Spring's built-in `DataSourceHealthIndicator`, via HikariCP), Disk Space (Spring's built-in `DiskSpaceHealthIndicator`), and a custom **GitHub health indicator** (`github`).

**Why liveness never depends on the database or GitHub.** An orchestrator (Kubernetes, ECS, etc.) restarts a container when its liveness probe fails. If liveness included the database, a transient Postgres blip would cause the orchestrator to kill and restart a perfectly healthy GateKeeper process — the restart does nothing to fix the database, and now there's a cold-start on top of the outage. Readiness, by contrast, *should* include the database: an instance that can't reach Postgres genuinely can't serve most requests, and an orchestrator routing traffic away from it (without killing it) is the correct response. This split is configured via `management.endpoint.health.group.readiness.include: readinessState,db` and `group.liveness.include: livenessState`.

**GitHub health indicator (`com.gatekeeper.github.GitHubHealthIndicator`).** Deliberately makes **no live GitHub API call** — it only reads the already-tracked `GitHubInstallation` rows (their `status`: `CONNECTING`/`ACTIVE`/`SYNCING`/`ERROR`/`DISCONNECTED`, from Milestone 8's onboarding work) and reports counts by status. It reports:

- `UNKNOWN` if no GitHub App is configured (`gatekeeper.github.app.id` is `0`) — this is a normal, expected state for a deployment that doesn't use GitHub integration, not a failure.
- `UP` otherwise, always — even if every installation is in `ERROR`. A GitHub sync problem is an actionable detail surfaced in the response body, not a reason to fail GateKeeper's own health and potentially trigger a restart or route traffic away from an instance that is otherwise completely healthy. Deterministic analysis (Policy Engine, Security Engine) has no dependency on GitHub health at all.

---

## 3. Metrics

Exposed at `GET /actuator/metrics` (Micrometer's own browsable endpoint, one metric per request) and `GET /actuator/prometheus` (Prometheus text-exposition format, all metrics in one scrape). Every metric is tagged with `application=gatekeeper-core` (`management.metrics.tags.application`).

**Cardinality discipline.** Every tag below is a small, fixed enum or annotation-supplied constant. Repository id, Pull Request id, User id, and Organization id are never used as a tag — doing so would make each metric's cardinality grow without bound as the platform is used, which is the single most common way a metrics system degrades in production.

JVM, HikariCP connection pool, and standard HTTP server metrics (`http.server.requests`, tagged by URI template/method/status — never by raw path or query string) are provided automatically by Micrometer's own auto-configuration; they are not listed individually below.

| Metric | Type | Tags | What it means |
|---|---|---|---|
| `gatekeeper.auth.login.attempts` | Counter | `outcome=success\|failure` | Login attempts. Never tagged with the attempted email. |
| `gatekeeper.auth.token.refresh` | Counter | `outcome=success\|failure` | Refresh-token exchanges. |
| `gatekeeper.webhook.signature.failures` | Counter | — | Inbound GitHub webhooks that failed signature verification. |
| `gatekeeper.webhook.events` | Counter | `event_type`, `outcome=processed\|ignored` | Every inbound GitHub webhook, by event type. |
| `gatekeeper.audit.events` | Counter | `event_type` | Every audit log entry recorded (Milestone 7). |
| `gatekeeper.github.token.cache.size` | Gauge | — | Current size of the installation access-token cache. |
| `gatekeeper.github.token.cache.access` | Counter | `outcome=hit\|refreshed` | Installation token cache hit/refresh rate. |
| `gatekeeper.errors.total` | Counter | `category`, `error_code` | Every exception handled by `GlobalExceptionHandler` — see Section 5. |
| `gatekeeper.operation.duration` | Timer | `operation`, `category`, `outcome=success\|error` | Duration of every `@ObservedOperation`-annotated call — see Section 4. |

**Authorization denials** are visible as `gatekeeper.errors.total{category="authorization"}` (an `AccessDeniedException` reaching `GlobalExceptionHandler`) rather than a separate metric — RBAC denial is a specific case of the general error-monitoring path, not a distinct subsystem.

---

## 4. Performance Monitoring: `@ObservedOperation`

Rather than a broad AOP pointcut instrumenting every method inside a package (which would time methods nobody asked to monitor, and make it unclear from reading a class which of its methods are actually observed), performance monitoring is **annotation-driven**: a method is timed and slow-call-logged only if it carries `@ObservedOperation`.

```java
@ObservedOperation(value = "github.fetchPullRequestFiles", category = OperationCategory.GITHUB_API)
public List<PullRequestFile> fetchPullRequestFiles(...) { ... }
```

`ObservedOperationAspect` (`@Around` on `@annotation(observedOperation)`) records the `gatekeeper.operation.duration` Timer described above and logs a `WARN` when the call exceeds its category's configured threshold:

```
Slow operation: 'policy.evaluate' (POLICY_ENGINE) took 1450ms, exceeding the 1000ms threshold.
```

**Categories and default thresholds** (`gatekeeper.observability.thresholds.*`, each independently overridable by environment variable):

| Category | Annotated method(s) | Default threshold | Env var |
|---|---|---|---|
| `GITHUB_API` | `GitHubApiClient`'s 5 public methods (mint token, fetch PR files, list repositories, create/update check run) | 3000ms | `OBSERVABILITY_THRESHOLD_GITHUB_API_MS` |
| `POLICY_ENGINE` | `PolicyEngine.evaluate` | 1000ms | `OBSERVABILITY_THRESHOLD_POLICY_MS` |
| `SECURITY_ENGINE` | `SecurityEngine.evaluate` | 1000ms | `OBSERVABILITY_THRESHOLD_SECURITY_MS` |
| `REVIEW_ENGINE` | `AIReviewEngine.evaluate` | 30000ms | `OBSERVABILITY_THRESHOLD_REVIEW_MS` |
| `ANALYSIS_PIPELINE` | `AnalysisExecutionService.onAnalysisRunReady` | 5000ms | `OBSERVABILITY_THRESHOLD_ANALYSIS_MS` |

These defaults reflect each category's normal cost (an LLM-backed review naturally takes far longer than a deterministic rule pass), not a formal SLA.

**Retried operations.** When an `@ObservedOperation`-annotated method is also `@Retryable` (e.g. `GitHubApiClient.fetchPullRequestFiles`), the aspect times each individual attempt, not the cumulative retried operation — every attempt genuinely is a separate GitHub API call, so this is the more accurate signal, not a degraded one.

---

## 5. Error Monitoring

`GlobalExceptionHandler` — the same class that has always mapped exceptions to GateKeeper's standard error response shape — now also logs and increments `gatekeeper.errors.total{category, error_code}` for every exception it handles. No response body, status code, or `ApiErrorResponse` shape changed; this is purely additive.

| Category | `error_code`(s) | Log level | Handled exception(s) |
|---|---|---|---|
| `validation` | `GK-400`, `GK-422` | INFO | Bean validation failures, constraint violations, unparseable request bodies, invalid enum query params |
| `authentication` | `GK-401` | WARN (bad credentials) / INFO (other) | `BadCredentialsException`, other `AuthenticationException`s |
| `authorization` | `GK-403` | INFO | `AccessDeniedException` — RBAC correctly denying an unauthorized caller is the system working as designed, not an anomaly |
| `business` | `GK-404`, `GK-409` | WARN (`ApiException`) / INFO (`NoResourceFoundException`) | Domain `ApiException`s (not-found, conflict); requests to a path with no matching handler or static resource — including a syntactically valid but non-allowlisted Actuator endpoint (e.g. `/actuator/env`), which would otherwise fall through to the generic 500 handler and misrepresent a routine "not found" as an unexpected error |
| `database` | `GK-409` | WARN | `DataIntegrityViolationException` (FK/unique constraint violations reaching the database without an application-level pre-check) |
| `unexpected` | `GK-500` | ERROR (with stack trace) | Anything else |

**Why there is no `GitHubApiException`/`AIProviderException` handler here.** Neither exception family ever reaches `GlobalExceptionHandler` in GateKeeper's actual control flow — both are always caught inside their own async orchestration layer first (`AnalysisExecutionService`, `AIReviewExecutionService`, `GitHubRepositorySyncService`), which is where their failures are already logged and, as of this milestone, timed with an `outcome=error` tag by `ObservedOperationAspect`. Adding a handler here for an exception type that structurally cannot arrive would be dead code, not additional coverage.

**Never logged, in any handler:** the attempted login email, JWTs, the GitHub webhook secret, GitHub App private keys, or raw webhook payloads.

---

## 6. Structured Logging

Every log line is enriched via SLF4J's MDC with:

| Field | Source | Notes |
|---|---|---|
| `correlationId` | `CorrelationIdFilter` | Reuses an incoming `X-Correlation-Id` header if present, otherwise generates one; echoed back on the response. Ties every log line and every `AuditLog` row (Milestone 7) written while handling a request back to it. Since a caller can supply it, it is a tracing aid only — never a trust boundary. |
| `requestId` | `CorrelationIdFilter` | Always server-generated, never taken from a header. Identifies exactly one HTTP call, distinct from `correlationId`, which a client can deliberately share across several logical calls. |
| `userId`, `organizationId` | `JwtAuthenticationFilter` | Populated once authentication resolves; absent on unauthenticated requests (e.g. login itself). |
| `serviceName` | Log encoder config | Static `gatekeeper-core`, added by the JSON encoder (production) as a `customFields` entry — present so log aggregation across multiple services doesn't need a separate source tag. |

**MDC lifecycle.** `CorrelationIdFilter` (highest filter precedence) is the single owner of the whole request's MDC lifecycle: its `finally` block calls `MDC.clear()` — not just removing its own two keys — so anything added later in the chain (`userId`, `organizationId`, or an ad hoc key a specific handler adds) is guaranteed cleared before the underlying servlet-container worker thread is returned to the pool and reused for an unrelated request.

**Propagation across `@Async`.** MDC is thread-local by design, which would normally mean it's lost the moment work crosses onto an `@Async` executor thread (analysis execution, AI review execution). `MdcTaskDecorator` (`org.springframework.core.task.TaskDecorator`, registered on both `AsyncConfig` executors) copies the submitting thread's MDC snapshot onto the worker thread before the task runs, and restores the worker thread's *own* prior MDC state afterward — restoring, not clearing, because pooled worker threads are reused across many unrelated tasks, unlike a per-request Tomcat thread.

**Local vs. production format** (`backend/src/main/resources/logback-spring.xml`, profile-conditional):

- **`local` profile** — human-readable console pattern including `[correlationId]`, easy to read while developing.
- **Every other profile** (`dev`, `prod`, …) — JSON via `logstash-logback-encoder`'s `LogstashEncoder`, with `correlationId`/`requestId`/`userId`/`organizationId` as top-level JSON fields (via `includeMdcKeyName`) and a static `service: gatekeeper-core` field — ready to ship directly to a log aggregator (ELK, Loki, CloudWatch Logs, etc.) without a separate parsing step.

Logger levels are controlled the same way they always were (`logging.level.root`/`logging.level.com.gatekeeper` in `application.yml`/profile overlays) — the new `logback-spring.xml` deliberately does not hardcode levels, only the output format.

---

## 7. Application Information

`GET /actuator/info` reports:

| Field | Source |
|---|---|
| `build.version`, `build.time` | Spring Boot's `build-info` Maven plugin goal (`pom.xml`) |
| `git.commit.id` (full and abbreviated), `git.branch`, `git.commit.time` | `git-commit-id-maven-plugin`, deliberately restricted to just these four properties (`includeOnlyProperties`) — committer email and full commit message are never exposed |
| `java.version`, `java.vendor` | `ApplicationInfoContributor` |
| `spring-boot.version` | `ApplicationInfoContributor` |
| `deployment.environment` | `ApplicationInfoContributor`, from `DEPLOYMENT_ENVIRONMENT` — a case-insensitive substring match (`prod`→Production, `stag`→Staging, `dev`→Development, `local`→Local, anything else passed through as-is, blank→Unknown) |

**Known limitation — Docker builds.** `docker-compose.yml` scopes the backend's build context to `./backend`, and `.git` lives at the repository root, so a Docker-built image has no `.git` directory available at build time. `git-commit-id-maven-plugin` is configured with `failOnNoGitDirectory: false`, so the build still succeeds — a Docker-built image's `/actuator/info` simply won't have real `git.*` values. A `mvn clean package` run directly (CI, or `./mvnw spring-boot:run` locally) is unaffected, since Maven searches upward from the POM's own location for `.git` regardless of any Docker build-context boundary.

---

## 8. Startup Monitoring

`GateKeeperApplication.main()` registers a `BufferingApplicationStartup` (2048-event buffer), which Spring Boot uses to populate `GET /actuator/startup` — a breakdown of every startup step and how long it took, useful for diagnosing slow boots without any custom instrumentation.

---

## 9. Docker

`backend/Dockerfile` declares a `HEALTHCHECK` that polls the **liveness** probe specifically:

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health/liveness || exit 1
```

Liveness, not general health, is deliberate: a database or GitHub outage should not cause the container orchestrator to kill and restart an otherwise-healthy process (see Section 2). `start-period=45s` gives Flyway migrations and JPA schema validation time to finish before the first check counts against `--retries`.

`docker-compose.yml` publishes the management port (`${MANAGEMENT_SERVER_PORT:-8081}:8081`) alongside the application port, for local development convenience — curl it directly from the host. As noted in Section 1, a real production deployment must not publish this port externally.

---

## 10. OpenTelemetry Compatibility (Future)

OpenTelemetry is **not** implemented in this milestone, but nothing here forecloses it:

- Micrometer (already in use for every metric in this document) has a first-class OpenTelemetry bridge (`micrometer-registry-otlp` / `micrometer-tracing`) — adding it later is a dependency and configuration change, not a rewrite of any metric already defined here.
- `correlationId`/`requestId` in MDC are conceptually equivalent to a trace/span id; a future Micrometer Tracing integration would either replace them with real trace context or propagate alongside it.
- The JSON log format (Section 6) already emits structured, machine-parseable fields, which is the same shape OpenTelemetry log correlation expects (trace/span id fields would simply be added alongside `correlationId`).
- Nothing here calls a proprietary metrics/tracing SDK directly — every integration point is either standard Micrometer or standard SLF4J/Logback, both of which have first-class OpenTelemetry support upstream.

Adopting Jaeger, Tempo, Zipkin, or an OTLP collector in the future is additive work on top of this foundation, not a replacement of it.

---

## Related Documents

- [API-Design.md](./API-Design.md) — the business-facing `/api/v1` REST API (Actuator is a separate, operational surface — see Section 1 above).
- [Architecture.md](./Architecture.md) — system layers and architecture decisions, including ADR-008 (Observability is cross-cutting, not a seventh layer).
- [../INSTALLATION.md](../INSTALLATION.md) — environment variables, including the management port and threshold overrides documented here.
