# GateKeeper

# API Design

**Version:** 1.0  
**Status:** Approved  
**Document Owner:** GateKeeper Team

---

# Purpose

This document defines the REST API surface for GateKeeper.

The API is responsible for exposing GateKeeper's capabilities to the web dashboard, future CLI clients, and third-party integrations.

This document intentionally defines API contracts only.

Implementation details, request validation, DTOs, authentication middleware, and controller logic are part of the implementation phase.

---

# API Design Principles

GateKeeper APIs follow these principles:

- RESTful resource-oriented design
- Stateless communication
- JSON request and response payloads
- JWT authentication
- Role-Based Access Control (RBAC)
- Predictable HTTP status codes
- Versioned APIs
- Idempotent operations where applicable

---

# API Version

```
/api/v1/
```

Future breaking changes will be introduced under a new API version.

---

# Authentication

All APIs except authentication endpoints require a valid JWT access token.

```
Authorization: Bearer <token>
```

---

# API Groups

## Authentication API

Responsible for authentication and session management.

### Endpoints

```
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh
GET    /api/v1/auth/me
```

`login` and `refresh` are rate-limited (Milestone 10: Security Hardening) - `login` independently by client IP and by the attempted account email, `refresh` by client IP. Exceeding either returns `429` via the standard error envelope. See [Security-Hardening.md](./Security-Hardening.md) for thresholds.

---

## User API

Responsible for user management.

### Endpoints

```
GET    /api/v1/users
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
```

---

## Role API

Responsible for RBAC management.

### Endpoints

```
GET    /api/v1/roles
POST   /api/v1/roles
PUT    /api/v1/roles/{id}
DELETE /api/v1/roles/{id}
```

---

## Repository API

Responsible for repository lifecycle.

### Endpoints

```
GET    /api/v1/repositories
GET    /api/v1/repositories/{id}

PUT    /api/v1/repositories/{id}

DELETE /api/v1/repositories/{id}
```

No `POST`: GitHub App installation is the only supported way a repository enters GateKeeper (Milestone 8: Repository Onboarding - see the GitHub Installations API below). A repository created any other way would have no `githubRepositoryId`/`githubInstallation` linkage and could never resolve a real `pull_request` webhook, so manual creation was removed rather than left as a dead end. `PUT`/`DELETE` remain - editing or disconnecting an already-linked repository is still meaningful.

---

## GitHub Installations API

Visibility into GitHub App installations and the URL that starts GitHub's own "install this App" flow (Milestone 8: Repository Onboarding). This is the API surface behind the Repositories page's "GitHub Connections" section - see [GitHub-Integration-Architecture-Review.md](./GitHub-Integration-Architecture-Review.md) for how this fits into the wider GitHub integration.

### Endpoints

```
GET  /api/v1/github/install-url

GET  /api/v1/github/installations

GET  /api/v1/github/installations/{id}

POST /api/v1/github/installations/{id}/sync
```

`GET /api/v1/github/install-url` returns `{"url": "...", "appConfigured": true|false}`. The GitHub App's id and slug never leave the backend - only the fully-formed URL is exposed, or `appConfigured=false` (with `url: null`) when the App isn't configured, so the frontend can show "GitHub App not configured" instead of a broken link. Requires `REPOSITORY_MANAGE`.

`GET /api/v1/github/installations` lists every known installation (including disconnected ones, so history isn't hidden), each with its lifecycle `status`, `lastSuccessfulSyncAt`, `lastSyncError`, and a denormalized `repositoryCount`. `GET .../installations/{id}` returns one. Both require only `WORKSPACE_READ` - installation visibility is informational, the same transparency posture already given to repositories themselves.

`POST /api/v1/github/installations/{id}/sync` synchronously re-runs the same repository reconciliation (`GET /installation/repositories` &rarr; upsert) that already runs asynchronously after every `installation` webhook - used by the post-install callback page and a manual "Resync now" action, so a user doesn't have to wait on the async webhook round trip to see their repositories appear. Requires `REPOSITORY_MANAGE`. Rate-limited per caller (Milestone 10: Security Hardening).

**Lifecycle status.** Each installation carries one of `CONNECTING` (row just created, no sync completed yet), `SYNCING` (a synchronization is in progress right now), `ACTIVE` (last synchronization succeeded), `ERROR` (last synchronization failed - `lastSyncError` holds why), or `DISCONNECTED` (GitHub reported the installation deleted). This is distinct from the existing `active` boolean, which only answers "does this installation still exist on GitHub" - `status` answers the finer-grained "is repository synchronization currently healthy," so a sync failure is visible in the UI instead of silently leaving repositories stale.

---

## GitHub Integration API

Receives webhook events from GitHub.

These endpoints are not intended for frontend consumption.

### Endpoints

```
POST /api/v1/github/webhook
```

Responsibilities

- Rate-limit check (Milestone 10: Security Hardening - one global bucket, checked before signature verification so a flood of forged deliveries is rejected before paying the HMAC cost)
- Verify webhook signature
- Receive Pull Request events
- Queue analysis

---

## Pull Request API

Provides Pull Request information - the reviewer's primary workspace (Milestone 1).

### Endpoints

```
GET /api/v1/pull-requests

GET /api/v1/pull-requests/{id}
```

Read-only: Pull Requests are created and updated only by the `pull_request`
webhook, never by a client of this API.

`GET /api/v1/pull-requests` is filterable by `repositoryId` and `status`,
paginated, and defaults to sorting by `updatedAt` descending. Each row is
enriched with its most recently created AnalysisRun's status and verdict
outcome (both null if no AnalysisRun exists yet), and with GitHub-facing
metadata (`number`, `githubUrl`, `repositoryOwner`, `repositoryName`) so the
frontend can link out to GitHub directly.

`GET /api/v1/pull-requests/{id}` returns the same GitHub metadata plus the
Pull Request's complete AnalysisRun history (newest first, each with its own
verdict outcome) inline - there is no separate `/analysis` sub-resource, so a
client gets the full picture in one request.

---

## Review Decision API

Lets a human reviewer record an APPROVE/REJECT decision against an Analysis Run, and lists that run's full decision history (Milestone 2: Reviewer Decision Workflow).

### Endpoints

```
POST /api/v1/analysis-runs/{id}/review-decisions

GET  /api/v1/analysis-runs/{id}/review-decisions
```

`POST` accepts `{"decision": "APPROVED" | "REJECTED", "comment": "..."}` (comment optional, max 2000 characters), records it against the authenticated caller, and returns 201 with the created decision. `GET` returns the full history for that run, newest first.

Write-once: a reviewer changing their mind creates a new decision rather than editing a previous one, so the complete history is always preserved. Recording a decision has no effect on the Analysis Run, Verdict, or Pull Request - it is purely additive, observed data. No role restriction exists on who may submit a decision, and no self-review restriction.

**GitHub write-back (Milestone 4).** Recording a decision asynchronously publishes it to a separate GitHub Check Run named "GateKeeper Review" on the pull request's head commit (`APPROVED` &rarr; conclusion `success`, `REJECTED` &rarr; conclusion `failure`; the check's output names the reviewer, the decision, the optional comment, and the decision timestamp). A later decision on the same Analysis Run updates that same check rather than creating a duplicate, always reflecting the latest decision. This is a deliberately separate check from the Verdict-driven one - analysis and human review remain two independent sources of truth, consistent with this document's own guiding principle that merge decisions originate only from deterministic engines. Publication is best-effort: a repository with no linked GitHub installation, or any GitHub API failure, never affects the decision itself, which is already durably recorded before publication is attempted.

---

## Analysis API

Represents Analysis Runs.

### Endpoints

```
GET /api/v1/analysis

GET /api/v1/analysis/{id}

POST /api/v1/analysis/{id}/rerun
```

---

## Findings API

Returns findings generated by analysis engines.

### Endpoints

```
GET /api/v1/findings

GET /api/v1/findings/{id}

GET /api/v1/analysis/{id}/findings
```

---

## Reports API

Unified Engineering Report.

### Endpoints

```
GET /api/v1/reports

GET /api/v1/reports/{id}

GET /api/v1/pull-requests/{id}/report
```

---

## Policies API

Organization-scoped configuration of the Policy Engine's rules (Milestone 6: Policy Management). See [Policy-Development.md](./Policy-Development.md) for how rules are written and why the rule catalog is never duplicated in the database.

### Endpoints

```
GET    /api/v1/policies

PUT    /api/v1/policies/{ruleId}

DELETE /api/v1/policies/{ruleId}
```

No `POST`: the rule catalog is entirely code-defined (`PolicyRule` beans discovered by Spring) - organizations configure an existing rule, they never create one. `{ruleId}` is one of those beans' stable `id()` (e.g. `TODO_COMMENT`); an unrecognized id 404s.

`GET` returns every discovered rule merged with the calling user's organization's effective configuration - `ruleId`, `description`, `defaultCategory`, `defaultSeverity` always reflect the live rule, never a database row. Requires `WORKSPACE_READ` (every role).

`PUT` accepts `{"enabled": true|false, "severityOverride": "LOW"|"MEDIUM"|"HIGH"|"CRITICAL"|null}` and upserts the organization's override. `DELETE` resets the rule to its default (idempotent - deletes the override row if one exists). Both require `POLICY_MANAGE` (ADMINISTRATOR, PLATFORM_ENGINEER - see [Authorization-Model.md](./Authorization-Model.md)).

A configuration change only affects *future* analysis runs; every already-persisted `PolicyFinding` and completed `AnalysisRun` permanently reflects the configuration in effect when it was produced.

---

## Audit Log API

The authoritative, immutable history of governance actions across the platform (Milestone 7: Enterprise Audit Logging). Answers who performed an action, what changed, when, and in which repository/pull request/analysis run/organization.

### Endpoints

```
GET /api/v1/audit-logs

GET /api/v1/audit-logs/{id}

GET /api/v1/audit-logs/export
```

No write endpoints of any kind - entries are created only by `AuditLogService.record`, called synchronously (same transaction as the action itself, not async/best-effort like GitHub check-run publishing) from every producer that generates a governance event: `ReportPublicationService`, `AnalysisResultPersistenceService` (Verdict), `ReviewDecisionService`, `PolicyConfigurationService`, `RepositoryService`, `UserService`, and `RoleService`. There is no update or delete endpoint, and none will ever be added - an audit record must never be edited once persisted.

`GET /api/v1/audit-logs` is filterable by `eventType`, `repositoryId`, `pullRequestId`, `analysisRunId`, `actorId`, `occurredAfter`, `occurredBefore`, paginated, and defaults to sorting by `occurredAt` descending. Always implicitly scoped to the caller's own organization - there is no `organizationId` query parameter, so a caller can never see another organization's audit trail. `GET /api/v1/audit-logs/{id}` returns a single entry, 404ing if it doesn't exist or belongs to a different organization. `GET /api/v1/audit-logs/export` streams every entry matching the same filters as CSV (ignoring pagination), for offline/compliance review. All three require `AUDIT_LOG_READ` (see [Authorization-Model.md](./Authorization-Model.md)).

**Structured data, not just free text.** Per this milestone's design direction, each entry's `summary` is a human-readable presentation field only - the durable record is structured: `eventType`, `actorId`/`actorName` (who; null for system-produced events like `VERDICT_PRODUCED`), `targetType`/`targetId` (what was acted on, populated only for `USER`/`ROLE`/`POLICY_RULE` events that have no dedicated scope column), and `oldValue`/`newValue` (JSON objects capturing what changed, null when an event has no natural before/after - e.g. a brand-new resource has no "old" state). `repositoryId`, `pullRequestId`, and `analysisRunId` are populated whenever the event is scoped to one, independent of `targetType`/`targetId`.

**Correlation id.** Every entry carries a `correlationId` shared by every audit event produced while handling the same HTTP request (see `com.gatekeeper.config.CorrelationIdFilter`) - not surfaced in the UI yet, but present in the model and the API response for future request-tracing.

---

## Dashboard API

Provides aggregated metrics.

### Endpoints

```
GET /api/v1/dashboard

GET /api/v1/dashboard/overview

GET /api/v1/dashboard/statistics
```

---

# Standard Response Format

Every successful response follows a consistent structure.

```json
{
  "success": true,
  "message": "Operation completed successfully.",
  "data": {}
}
```

---

# Standard Error Response

```json
{
  "success": false,
  "error": {
    "code": "GK-404",
    "message": "Repository not found."
  }
}
```

---

# HTTP Status Codes

| Status | Meaning |
|----------|---------|
| 200 | Success |
| 201 | Resource Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 422 | Validation Failed |
| 429 | Too Many Requests (Milestone 10: Security Hardening - see [Security-Hardening.md](./Security-Hardening.md)) |
| 500 | Internal Server Error |

---

# Authentication & Authorization

Authentication

- JWT Access Token
- Refresh Token

Authorization

- Permission-based Role-Based Access Control (Milestone 5). Controllers authorize on a fixed set of permissions (e.g. `WORKSPACE_READ`, `REVIEW_DECISION_CREATE`) rather than role names directly - see [Authorization-Model.md](./Authorization-Model.md) for the full role/permission matrix, the deny-by-default rule for unrecognized roles, and how to extend the model.

Supported Roles

- Developer
- Technical Lead
- Engineering Manager
- Platform Engineer
- DevSecOps Engineer
- Administrator

---

# Pull Request Analysis Flow

```
GitHub

↓

Webhook

↓

Repository Validation

↓

Create Analysis Run

↓

Policy Engine

↓

Security Engine

↓

AI Review Engine

↓

Verdict Engine

↓

Engineering Report

↓

Dashboard Update

↓

GitHub Status Check
```

---

# API Security

All APIs should enforce:

- HTTPS only
- JWT Authentication
- RBAC authorization
- Input validation
- Rate limiting
- Request logging
- Audit logging
- Secure webhook signature verification

---

# API Design Rules

Every endpoint must:

- Follow REST naming conventions
- Return consistent JSON responses
- Never expose internal database identifiers unnecessarily
- Validate all request payloads
- Return meaningful HTTP status codes
- Generate audit events where applicable

---

# Operational Endpoints (Not Part of `/api/v1`)

GateKeeper also exposes health, metrics, and application-info endpoints via Spring Boot Actuator (Milestone 9: Observability) — `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, `/actuator/startup`. These are **not** business API endpoints: they are served on a separate management port, are not versioned under `/api/v1`, require no JWT (isolated by network reachability instead — see below), and are not intended for the frontend or any third-party client. See [Observability.md](./Observability.md) for the full reference and the reasoning behind serving them on a separate port rather than gating them with a new RBAC permission.

---

# Future APIs

Future versions may introduce:

- Plugin API
- CLI API
- Public SDK API
- GraphQL API
- WebSocket Notifications
- Bulk Analysis API
- Multi-Organization API
- AI Policy Generation API

---

# Guiding Principles

The GateKeeper API is designed around business capabilities rather than internal implementation.

- APIs expose business resources.
- Internal modules remain hidden.
- Every request is authenticated.
- Every operation is auditable.
- Merge decisions originate only from deterministic engines.
- AI findings are always advisory.