# GitHub Integration — Architecture Review (Post-Phase 1)

**Reviewer stance:** Staff-level architecture review, conducted before approving Phase 2. Every claim below is traced to the actual source in this repository — file paths and, where useful, line-level behavior are cited directly rather than paraphrased from memory. Nothing here proposes a rewrite; the existing package structure, dependency-injection style, and coding conventions are treated as the baseline to extend, not replace.

---

## 0. Verified Current Status

Phase 1 is real and correctly built, not a stub:

- GitHub App JWT authentication (`GitHubAppAuthService.mintAppJwt`) — real RS256 signing.
- Installation access token minting and caching (`GitHubAppAuthService.getInstallationAccessToken`).
- Webhook signature verification (`WebhookSignatureVerifier`) — HMAC-SHA256, constant-time comparison.
- `installation` and `installation_repositories` onboarding, persisted to real tables (`github_installations`, `repositories`), verified end-to-end against a live installation (`arcinth`, installation id `147259549`).
- `pull_request` ingestion → `AnalysisRun` creation → full deterministic pipeline (Policy Engine, Security Engine, Verdict Engine) → Unified Engineering Report — all implemented, none of it stubbed.

What is **not** yet built, confirmed by direct inspection (not inferred): any code path that writes back to GitHub. `GitHubApiClient` — "the only class permitted to issue outbound HTTP calls to the GitHub REST API" (its own class Javadoc, `GitHubApiClient.java:20-23`) — has exactly two methods: `mintInstallationAccessToken` and `fetchPullRequestFiles`. No check-run creation, no comment posting, no commit-status API call exists anywhere in the codebase. GateKeeper today is a **read-and-report** system: it observes pull requests and renders findings inside its own dashboard. It does not yet communicate back onto the pull request itself.

---

# 1. CURRENT ARCHITECTURE

## Webhook Request Lifecycle

1. GitHub delivers a webhook to the Cloudflare Tunnel URL, which forwards to `localhost:8080`.
2. `GitHubWebhookController.receiveWebhook` (`POST /api/v1/github/webhook`) receives the raw request: the body as `byte[]`, plus three headers — `X-Hub-Signature-256`, `X-GitHub-Event`, `X-GitHub-Delivery`. The endpoint is excluded from JWT authentication (`@SecurityRequirements`, `SecurityConfig`'s `permitAll` for this path) — GitHub cannot present a bearer token, so trust here comes entirely from signature verification.
3. `WebhookSignatureVerifier.verify(payload, signature)` computes HMAC-SHA256 over the *raw* bytes (not the parsed JSON — verification must happen before any deserialization, since re-serializing a parsed payload would not reproduce GitHub's original signature) and compares against the header using `MessageDigest.isEqual` (constant-time, avoiding a timing side-channel). A mismatch throws `InvalidWebhookSignatureException`.
4. `GitHubEventRouter.route(eventType, payload, deliveryId)` dispatches by the `X-GitHub-Event` header value. Exactly three event types are recognized; every other value falls through to a single `log.info("Ignoring unsupported GitHub event type '{}' ...")` line and the method returns normally.
5. For a recognized type, the router calls a shared generic `parsePayload(payload, Class<T>)` helper (Jackson `ObjectMapper.readValue`), then calls exactly one method on exactly one handler, passing the whole parsed payload plus `deliveryId`.
6. The controller returns `200 OK` once `route()` returns without throwing. If signature verification or payload parsing throws, the exception propagates out of the controller to GateKeeper's global exception mapping (`ApiException` subtypes → structured error responses — `InvalidWebhookSignatureException` → 401, `MalformedWebhookPayloadException` → 400), which is what GitHub's "Recent Deliveries" panel shows as a non-200 response.

## Controller Responsibilities

`GitHubWebhookController` does exactly two things: extract the raw request, and delegate. It has no knowledge of event semantics, no branching logic, and (correctly) no `@Transactional` boundary of its own — it doesn't need one, since it never touches persistence directly.

## Event Routing

`GitHubEventRouter` is the single decision point for "which events matter." This is a deliberate, already-documented design choice (its own class Javadoc: *"GitHub sends many event types GateKeeper doesn't act on ... this is the single place that decides which ones matter"*). It currently recognizes:

```java
private static final String PULL_REQUEST_EVENT = "pull_request";
private static final String INSTALLATION_EVENT = "installation";
private static final String INSTALLATION_REPOSITORIES_EVENT = "installation_repositories";
```

Adding a new event type is structurally a two-line change (a new constant, a new `if` branch) plus a new handler method — the router itself imposes no real extension cost. This matters for the roadmap below.

## Event Handlers

| Event | Handler | Transaction shape |
|---|---|---|
| `pull_request` | `AnalysisOrchestrator.handlePullRequestEvent` | One synchronous `@Transactional` method: validates payload shape, resolves the linked `Repository` (`RepositoryLookupService`), upserts the `PullRequest`, and — for `opened`/`reopened`/`synchronize` only — creates and queues an `AnalysisRun`, all before the HTTP response is sent. |
| `installation` | `GitHubInstallationService.handleInstallationEvent` | One `@Transactional` method: upsert-or-deactivate `GitHubInstallation` by `installationId`. |
| `installation_repositories` | `RepositoryService.handleInstallationRepositoriesEvent` | One `@Transactional` method: for each entry in `repositories_added`, find-or-create a `Repository` and link it to the resolved `GitHubInstallation`; for each entry in `repositories_removed`, mark it inactive. |

None of the three ingestion handlers do anything asynchronous themselves — all synchronous work finishes (and commits) before the webhook HTTP response returns. The actual analysis pipeline execution is what becomes asynchronous, and only for `pull_request`.

## Services (Full Inventory, GitHub-Adjacent)

**GitHub integration proper** (`com.gatekeeper.github`): `GitHubAppAuthService`, `GitHubApiClient`, `GitHubInstallationService`, `WebhookSignatureVerifier`, `GitHubEventRouter`, `PemPrivateKeyReader`.

**Ingestion & orchestration** (`com.gatekeeper.orchestration`): `AnalysisOrchestrator` (ingestion), `AnalysisExecutionService` (deterministic pipeline execution, `@Async`), `AnalysisResultPersistenceService` (atomic findings + verdict + `COMPLETED` write), `AIReviewExecutionService` (AI pipeline execution, separate `@Async` executor), `AIReviewResultPersistenceService`, `ReportGenerationListener` + `ReportPublicationService` (Unified Engineering Report), `ReportTimeoutSweepJob` (`@Scheduled` fallback), plus three `*ContextFactory` classes that assemble each engine's input from the shared fetched-files list.

**Domain services touched by ingestion**: `PullRequestService` (upsert by `githubPrId`), `RepositoryLookupService` (read-only resolution by `githubRepositoryId`), `RepositoryService` (now also owns `installation_repositories` handling), `AnalysisRunService` (state machine: `RECEIVED → QUEUED → IN_PROGRESS → COMPLETED|FAILED`).

**Engines**: `PolicyEngineService`/`PolicyEngine`, `SecurityEngineService`/`SecurityEngine`, `VerdictEngine`, `AIReviewEngineService`/`AIReviewEngine` (delegates to a provider, e.g. `AnthropicAIReviewProvider`).

## Repositories & Entities

| Entity | Repository | Written by |
|---|---|---|
| `GitHubInstallation` | `GitHubInstallationRepository` | `GitHubInstallationService` |
| `Repository` | `RepositoryRepository` | `RepositoryService` (both REST CRUD and webhook-driven linking) |
| `PullRequest` | `PullRequestRepository` | `PullRequestService` |
| `AnalysisRun` | `AnalysisRunRepository` | `AnalysisRunService` |
| `PolicyFindingEntity` | `PolicyFindingRepository` | `AnalysisResultPersistenceService` |
| `SecurityFindingEntity` | `SecurityFindingRepository` | `AnalysisResultPersistenceService` |
| `Verdict` / `VerdictReasonEntity` | `VerdictRepository` / `VerdictReasonRepository` | `AnalysisResultPersistenceService` |
| `AIReviewRun` | `AIReviewRunRepository` | `AIReviewResultPersistenceService` |
| `AIReviewFindingEntity` | `AIReviewFindingRepository` | `AIReviewResultPersistenceService` |
| `EngineeringReport` / `AuditLog` | `EngineeringReportRepository` / `AuditLogRepository` | `ReportPublicationService` |

## Persistence Flow, End to End

```
GitHub ──HTTPS──▶ Cloudflare Tunnel ──▶ GitHubWebhookController
                                              │
                                     WebhookSignatureVerifier
                                              │
                                       GitHubEventRouter
                              ┌───────────────┼────────────────────┐
                              ▼               ▼                    ▼
                       "installation"  "installation_        "pull_request"
                              │         repositories"               │
                              ▼               │                    ▼
                  GitHubInstallationService    │           AnalysisOrchestrator
                              │                ▼                   │
                    github_installations  RepositoryService   ┌────┴─────┐
                                                │        PullRequestService
                                                ▼              AnalysisRunService
                                          repositories           │        │
                                                          pull_requests  analysis_runs
                                                                       (RECEIVED→QUEUED)
                                                                          │
                                                            ── HTTP 200 returned here ──
                                                                          │
                                                     AnalysisRunReadyForExecutionEvent
                                                     AIReviewRequestedEvent  (fan-out, after commit)
                                                        │                        │
                                                        ▼                        ▼
                                          AnalysisExecutionService (@Async)   AIReviewExecutionService (@Async,
                                                        │                     separate thread pool)
                                    fetch changed files (GitHubApiClient)          │
                                          │              │                  fetch changed files (again,
                                          ▼              ▼                   independently)
                                  PolicyEngineService  SecurityEngineService        │
                                          │              │                         ▼
                                          └──────┬───────┘                 AIReviewEngineService
                                                 ▼                                  │
                                   AnalysisResultPersistenceService                 ▼
                                (findings + VerdictEngine + COMPLETED,   AIReviewResultPersistenceService
                                 one atomic transaction)                  (ai_review_runs, ai_review_findings)
                                                 │                                  │
                                       VerdictProducedEvent              AIReviewFinishedEvent
                                                 └───────────────┬───────────────────┘
                                                                 ▼
                                                      ReportGenerationListener
                                                                 ▼
                                                      ReportPublicationService
                                                (publishes once both sides resolved,
                                                 or on ReportTimeoutSweepJob's fallback)
                                                                 ▼
                                                  engineering_reports, audit_logs
```

The two vertical branches under `AnalysisOrchestrator` (deterministic pipeline vs. AI review) are genuinely independent — this is a deliberate, already-documented architectural property (`AIReviewExecutionService`'s own Javadoc: *"the two are peer consumers of independent events ... not a chain"*), not an oversight. It's the concrete mechanism behind the Product Vision's *"Deterministic engines establish truth. AI provides judgment"* principle: there is no code path by which the AI side can delay, fail, or influence the deterministic side.

---

# 2. SUPPORTED EVENTS

| Event | Supported | Handler | Service | Current Behavior |
|---|---|---|---|---|
| `installation` | **Yes** | `GitHubEventRouter` → `GitHubInstallationService` | `GitHubInstallationService` | Upserts `github_installations` by `installation_id` on any action except `deleted`; sets `active = false` (row preserved) on `deleted`. Verified live. |
| `installation_repositories` | **Yes** | `GitHubEventRouter` → `RepositoryService` | `RepositoryService` | Links/creates `repositories` rows for `repositories_added`; sets `active = false` for `repositories_removed`. Requires the installation to already be known — logs and skips otherwise. Verified live. |
| `pull_request` | **Partial** | `GitHubEventRouter` → `AnalysisOrchestrator` | `AnalysisOrchestrator`, `AnalysisRunService`, `PullRequestService` | Only 4 of GitHub's ~15 possible `action` values are recognized (`PullRequestAction` enum): `opened`, `reopened`, `synchronize` create an `AnalysisRun` and trigger the pipeline; `closed` updates the `PullRequest`'s status (`MERGED`/`CLOSED`) but does **not** trigger analysis; every other action (`labeled`, `assigned`, `review_requested`, `edited`, `ready_for_review`, etc.) is a complete no-op — the PR row isn't even touched. This is a deliberate, minimal action set, not a bug. |
| `repository` | **No** | — | — | Falls through to the router's generic "ignoring unsupported event type" log line. A repository rename, transfer, or visibility change on GitHub is never reflected in `repositories.full_name`/`name`. |
| `push` | **No** | — | — | Not routed. GateKeeper has no concept of a direct push to a non-PR branch. |
| `pull_request_review` | **No** | — | — | Not routed. A human reviewer's Approve/Request Changes/Comment on GitHub has no effect on GateKeeper's own `Verdict`. |
| `pull_request_review_comment` | **No** | — | — | Not routed. |
| `issue_comment` | **No** | — | — | Not routed (this is also GitHub's event for a plain PR-level comment, not just issues). |
| `check_suite` | **No** | — | — | Not routed. GateKeeper has never created a check suite, so it would never receive a meaningful one back either. |
| `check_run` | **No** | — | — | Not routed. GateKeeper does not create check runs at all (confirmed: `GitHubApiClient` has no such method). |
| `workflow_run` | **No** | — | — | Not routed. No CI-status awareness exists anywhere in the codebase. |
| `workflow_job` | **No** | — | — | Not routed. |

---

# 3. ARCHITECTURAL GAPS

Organized by the specific areas requested. Each is graded against what's actually implemented, not what would be nice to have.

**Pull request ingestion.** Solid for the MVP action set. Gap: only 4 actions are recognized. Notably absent: `edited` (a PR retitled or its base branch changed doesn't update GateKeeper's copy), `ready_for_review` (a draft PR converted to ready doesn't trigger analysis — if GateKeeper is meant to skip draft PRs, this is actually a reasonable exclusion, but it's not documented as an intentional decision anywhere I can find, only inferable from the enum's silence).

**Repository synchronization.** There is no handling of the `repository` event at all. If a repository is renamed on GitHub, `repositories.full_name` silently goes stale — `RepositoryLookupService` still matches correctly (it keys on the immutable `github_repository_id`, not the name), so *analysis itself* wouldn't break, but the dashboard would show a wrong name indefinitely.

**GitHub REST API integration.** Two operations exist (`mintInstallationAccessToken`, `fetchPullRequestFiles`). No commit-status API, no check-runs API, no issue-comments API, no repository-metadata refresh call exists.

**GitHub App authentication.** Complete and correct for what's needed today (JWT minting, installation token exchange and caching). No gap here — this layer will not need to change to support any of the events below, since none of them need a different authentication mechanism.

**Changed file retrieval.** `fetchPullRequestFiles` retrieves the paginated files-changed list with per-file unified-diff `patch` text. This is what both engines actually consume. Gap: no full-commit-range diff, no individual commit metadata (author, message per commit) is ever fetched — irrelevant to Policy/Security engines as they exist today, but would matter if a future engine needed commit-level granularity (e.g., a commit-message-policy engine).

**Diff retrieval.** Covered by the above — per-file patches only, capped at `gatekeeper.analysis.max-changed-files-per-pull-request` (truncates and logs a warning beyond that).

**Policy Engine integration.** Complete — wired into `AnalysisExecutionService`, findings persisted atomically with the verdict.

**Security Engine integration.** Complete — same shape as Policy.

**AI Review Engine integration.** Complete and correctly isolated — independent event, independent thread pool, independent failure handling, structurally unable to affect the verdict (matches Product Vision's explicit non-goal: *"Allow AI to make governance decisions"*).

**Verdict Engine integration.** Complete — runs synchronously inside `AnalysisResultPersistenceService`'s single transaction, guaranteeing (per that class's own documented invariant) that a `COMPLETED` `AnalysisRun` always has exactly one `Verdict`, no exceptions.

**Check run creation.** **Not implemented — no code, no API method, no entity, nothing.** This is the largest genuine gap relative to a typical PR-governance product. Today, the *only* place a verdict is visible is GateKeeper's own dashboard; a developer working entirely inside GitHub's UI has no signal at all that GateKeeper evaluated their PR.

**PR comment publishing.** **Not implemented.** Same gap category as check runs — no `issue_comment`/PR-comment creation capability exists.

**Status reporting.** **Not implemented**, and worth being precise about scope here: neither the legacy Commit Status API nor the newer Checks API is used anywhere. GateKeeper currently has zero write-back capability to GitHub.

### A scope note worth surfacing explicitly

I checked `docs/Product-Vision.md` and `docs/Product-Backlog.md` before writing the roadmap below, and want to flag something directly: **check-run creation, PR comment publishing, and status reporting appear nowhere in either document.** The frozen MVP scope (`docs/Product-Vision.md`, "MVP Features") is: Repository Management, Authentication, RBAC, Pull Request Analysis, Policy Engine, Security Engine, AI Review Engine (Advisory Only), Unified Report Generation, Engineering Dashboard. GateKeeper's documented MVP is explicitly a **read-and-report** system — the report lives in GateKeeper's own dashboard, not back on the GitHub PR. Building check-run/comment write-back would be genuinely new product scope, not a gap in *implementing the documented MVP*. I'm not saying it's the wrong thing to build — it's a very natural next step for a governance product, and may well be exactly what "Phase 2" is meant to mean — but it should be a deliberate scope decision, not something silently assumed because the word "governance platform" implies it. Flagging this now, before any implementation, is exactly the kind of thing this review exists to catch.

One more precision point: the task framing above describes GateKeeper as an *"AI-powered pull request governance platform."* Per `docs/Product-Vision.md`'s own stated philosophy — *"Deterministic engines establish truth. AI provides judgment"*, and the product's own elevator pitch, *"Policy-Driven Pull Request Analysis Platform"* — that's not quite how the product is positioned. AI is explicitly advisory-only and structurally excluded from governance decisions. This isn't a pedantic distinction: if "Phase 2" planning starts from "AI-powered governance," it risks pulling the AI Review Engine toward influencing the verdict, which would contradict a principle this codebase enforces at the architecture level (separate events, separate thread pools, separate persistence paths, no code path from AI findings to `Verdict`). Worth keeping the framing as "deterministic governance, AI-assisted" going forward.

---

# 4. IMPLEMENTATION ROADMAP

Numbering continues from "Phase 1" (the completed GitHub App integration this review covers). Each phase is independently shippable and independently revertible.

## Phase 2 — `repository` Event Handling

**Objective:** Keep `repositories.name`/`full_name` in sync when a repository is renamed or transferred on GitHub, closing the one real data-staleness gap identified above.
**Required classes:** none new — extends `GitHubEventRouter` (new branch), extends `RepositoryService` (new method, same pattern as `handleInstallationRepositoriesEvent`), one new DTO (`RepositoryWebhookPayload`).
**Modified classes:** `GitHubEventRouter`, `RepositoryService`.
**Risks:** low. Purely additive; no existing behavior changes.
**Dependencies:** none — can ship independently of everything else in this roadmap.

## Phase 3 — Missing `pull_request` Actions (`edited`, `ready_for_review`)

**Objective:** Decide deliberately (not by omission) whether a retitled/rebased PR or a draft-to-ready transition should re-trigger analysis, and implement whichever answer is chosen.
**Required classes:** none new.
**Modified classes:** `PullRequestAction` (new enum constants), possibly `AnalysisOrchestrator` if `edited` should update the `PullRequest` row without creating a new `AnalysisRun`.
**Risks:** low technically; the real risk is behavioral — re-analyzing on every edit could multiply `AnalysisRun` volume significantly if not scoped carefully (e.g., only re-analyze `edited` when `base.sha` or `head.sha` actually changed, not on a title-only edit).
**Dependencies:** none.

## Phase 4 — GitHub Check Run Creation (write-back, part 1)

**Objective:** Publish GateKeeper's verdict back onto the pull request as a native GitHub check, so a developer sees the result without leaving GitHub. This is the highest-leverage write-back feature — it's what makes GateKeeper feel integrated rather than external.
**Required classes:** `CheckRunClient` or an addition to `GitHubApiClient` (new methods: create check run, update check run), a `CheckRunPublicationService` (mirrors `ReportPublicationService`'s idempotent-publish shape), a `checks:write` permission added to the GitHub App's manifest (operator action, not code).
**Modified classes:** `GitHubApiClient` (new methods), potentially a new listener on `VerdictProducedEvent` (reusing the existing event rather than inventing a new one).
**Risks:** medium. This is the first place GateKeeper writes to GitHub — needs its own retry/failure philosophy (a failed check-run *update* should not roll back or affect the already-committed `Verdict`, mirroring how AI Review failures don't affect the deterministic verdict today). Needs a new GitHub App permission grant, which existing installations won't have until reinstalled/accepted.
**Dependencies:** requires `VerdictProducedEvent` (already exists) and the installation access token flow (already exists) — no new auth work needed.

## Phase 5 — PR Comment Publishing (write-back, part 2)

**Objective:** Post a summary comment on the PR (findings count, verdict, link to the full report) for teams that prefer comment-based visibility over the Checks UI.
**Required classes:** `PullRequestCommentClient` (or `GitHubApiClient` extension), `PullRequestCommentPublicationService`.
**Modified classes:** `GitHubApiClient`.
**Risks:** medium-high — needs idempotency (don't repost a comment on every redelivery or every `synchronize`; likely needs to track "already commented" state, e.g. a new column or an upsert-by-marker strategy against GitHub's own comment API). Higher user-visible blast radius than check runs if done wrong (a spammy bot comment on every push is a fast way to get an App uninstalled).
**Dependencies:** ideally ships after Phase 4, reusing its retry/failure conventions rather than inventing a second one.

## Phase 6 — `pull_request_review` / `issue_comment` Ingestion (read side)

**Objective:** Let a human reviewer's GitHub-native review activity show up in GateKeeper's own report (e.g., "2 approvals, 1 change requested" alongside GateKeeper's own verdict) — read-only, no write-back.
**Required classes:** `PullRequestReviewWebhookPayload` DTO, a small persistence path (new entity or an extension of `PullRequest`) — this needs a design decision, not just a mechanical extension, since nothing today models "reviews" as a concept.
**Modified classes:** `GitHubEventRouter`.
**Risks:** low-medium. Mostly new modeling work, not integration risk.
**Dependencies:** none on Phases 2–5; could be reordered earlier if desired.

## Phase 7 — `check_suite` / `check_run` / `workflow_run` / `workflow_job` (CI-awareness)

**Objective:** Let GateKeeper's verdict optionally account for CI status (e.g., don't consider a PR mergeable-ready if tests are failing) — this is new product scope, not implied by anything in the current Product Vision.
**Required classes:** substantial — new DTOs, a `CiStatusService`, likely a new `VerdictRule` if CI status is meant to feed the Verdict Engine (architecturally straightforward, since `VerdictEngine` already runs all `VerdictRule` beans generically).
**Risks:** high relative to the rest of this roadmap — this is the first phase that would touch `VerdictEngine`'s actual rule set, and any change there needs to preserve the "deterministic, explainable, auditable" property the whole system is built around.
**Dependencies:** should not start before a product-vision-level decision on whether CI status is in scope at all — this is explicitly listed under neither MVP Features nor Future Scope in `docs/Product-Vision.md`.

## Phase 8 — `push` Event (non-PR branch awareness)

**Objective:** Unclear without a stated product need — flagging as the lowest-priority item in this roadmap. GateKeeper's entire model is PR-centric; a direct push event doesn't obviously map to anything GateKeeper currently does. Recommend deferring until a concrete use case is identified (e.g., branch-protection-adjacent policy enforcement) rather than building speculatively.
**Risks:** scope-creep risk if built without a driving use case.

---

# 5. CLASS-LEVEL PLAN

Only classes implicated by Phases 2–5 (the concretely-scoped, near-term phases); Phases 6–8 are deliberately left at the roadmap level above since they depend on product decisions not yet made.

| Class | Package | Status | Responsibility | Dependencies | Key methods |
|---|---|---|---|---|---|
| `RepositoryWebhookPayload` | `com.gatekeeper.github.dto` | **New** | DTO for the `repository` event (renamed/transferred actions) | none (Jackson record, same shape as sibling DTOs) | — |
| `RepositoryService.handleRepositoryEvent(...)` | `com.gatekeeper.repository` | **New method on existing class** | Updates `name`/`full_name`/`owner` on the matched `Repository` when GitHub reports a rename/transfer | `RepositoryRepository` (existing) | `handleRepositoryEvent(RepositoryWebhookPayload, String deliveryId)` |
| `GitHubEventRouter` | `com.gatekeeper.github` | **Modified** | Add `"repository"` branch | `RepositoryService` (already a dependency) | — |
| `PullRequestAction` | `com.gatekeeper.orchestration` | **Modified** | Add `EDITED`/`READY_FOR_REVIEW` constants once the re-trigger decision is made | none | — |
| `GitHubApiClient` | `com.gatekeeper.github` | **Modified** | Add `createCheckRun(...)`, `updateCheckRun(...)` | existing `RestClient` | new methods, same try/catch-and-wrap-as-`GitHubApiException` shape as existing methods |
| `CheckRunPublicationService` | `com.gatekeeper.orchestration` (co-located with `ReportPublicationService`, its closest architectural sibling) | **New** | Listens for `VerdictProducedEvent`, creates/updates a GitHub check run reflecting the verdict; idempotent per analysis run, same shape as `ReportPublicationService.publishIfAbsent` | `GitHubApiClient`, `GitHubAppAuthService`, `VerdictRepository` | `onVerdictProduced(Long analysisRunId)` |
| `PullRequestCommentPublicationService` | `com.gatekeeper.orchestration` | **New** | Posts/updates a summary comment; tracks whether one was already posted for this run | `GitHubApiClient`, `GitHubAppAuthService` | `onVerdictProduced(Long analysisRunId)` (reuses the same trigger event as check runs) |

Every new class above follows an already-established pattern in this codebase (a thin event listener + a persistence/API-calling service, mirroring `ReportGenerationListener`/`ReportPublicationService`) rather than introducing a new architectural shape.

---

# 6. DESIGN REVIEW

**SOLID.** Single Responsibility is unusually well kept for a codebase this size — `GitHubWebhookController` genuinely does only HTTP, `GitHubEventRouter` genuinely does only routing, each engine has its own thin `*EngineService` boundary separating pure computation from logging/orchestration concerns. Open/Closed holds well at the router level (adding an event type is additive) but less well inside `AnalysisOrchestrator` (the `PullRequestAction` enum is a closed set that must be edited, not extended, for new actions — acceptable for a small, deliberately curated action set, but worth naming as the pattern's limit). Dependency Inversion is consistently applied — every cross-module dependency is through a Spring-injected interface or concrete collaborator, never a static call.

**Clean/layered architecture.** The ingestion → execution → persistence → event → report pipeline is a textbook layered design, and the AOP self-invocation avoidance (separate beans for `@Async` entry points vs. `@Transactional` persistence, documented explicitly in three different classes' Javadoc) shows this was a conscious, understood constraint, not accidental correctness.

**Extensibility.** Strong for adding new event types (router) and new verdict rules (`VerdictEngine` runs all `VerdictRule` beans generically — a new rule is a new `@Component`, zero existing code touched). Weaker for adding new *engines* — Policy and Security each got their own parallel `*ContextFactory`/`*EngineService`/`*Result` triad rather than a shared abstraction; a fourth deterministic engine would mean a fourth near-identical triad. Not wrong at three engines (premature abstraction would be worse), but worth watching if a fourth is ever added — that's the point at which extracting a common `EngineService<C, R>` shape stops being speculative.

**Maintainability.** High. The Javadoc discipline throughout this codebase (explaining *why*, not *what*, and explicitly cross-referencing architecture decision records) is genuinely unusual and makes this review possible to write with confidence in the first place.

**Scalability.** The two separate `@Async` executors (deterministic vs. AI review) already prevent the slower LLM path from starving the deterministic pipeline's threads — a real scalability decision, not incidental. The one latent bottleneck: `GitHubApiClient.fetchPullRequestFiles` is called independently by both `AnalysisExecutionService` and `AIReviewExecutionService` for the same commit (an explicitly accepted tradeoff per `AIReviewExecutionService`'s own Javadoc, favoring isolation over one avoided API call) — fine at current volume, worth revisiting only if GitHub API rate limits become a real constraint.

**Testability.** Consistently high — every service takes its dependencies through constructor injection, every engine is a pure function over its context, and the existing test suite (unit tests at the router/service boundary, Testcontainers-based integration tests for the full pipeline) matches the actual risk profile of each layer rather than over- or under-testing uniformly.

**Suggested improvements (none requiring a rewrite):**
1. Document the `PullRequestAction` action set's intentionality directly in `docs/Architecture.md` (it's currently only inferable from a code comment) — future readers shouldn't have to reverse-engineer whether `edited` is missing on purpose.
2. When check-run/comment write-back is built (Phase 4/5), give it the same "never affects the already-committed verdict" isolation the AI Review path already has — that's the one architectural property worth protecting most carefully as write-back capability is added.
3. Consider whether `RepositoryService` accumulating both REST-CRUD responsibility and webhook-ingestion responsibility (as of the `installation_repositories` work) is a shape worth revisiting once a `repository` event handler is added on top of it — three responsibilities on one service class is still reasonable, but it's the class to watch if a fourth is added.

---

# 7. IMPLEMENTATION STRATEGY

**Highest-priority feature: GitHub Check Run creation (Phase 4).**

**Why this comes next, ahead of everything else in the roadmap:**

Phase 1 proved the entire pipeline works — a real PR, a real verdict, real findings, all correctly persisted. But right now, that verdict is invisible from where a developer actually works. They'd have to know to open GateKeeper's own dashboard to find out GateKeeper even ran. A governance platform whose governance decision isn't visible on the PR itself is not yet delivering its core value proposition, regardless of how correct the pipeline underneath it is. Every other gap identified in this review (repository sync, missing PR actions, review/comment ingestion, CI awareness) is a refinement; check-run visibility is the thing that makes Phase 1's work actually usable by a developer instead of only observable by an operator.

It's also the lowest-risk way to start write-back: GitHub's Checks API is designed to be idempotent-friendly (a check run can be created once and updated repeatedly), it has a well-defined neutral/failure state that maps naturally onto `APPROVED`/`BLOCKED`, and — critically — a failure to *update* a check run has no plausible path to corrupting anything already committed (the `Verdict` row is already durable before this would ever run).

**How it should be implemented:**

1. Extend `GitHubApiClient` with two new methods, following its existing method shape exactly (try/catch wrapping `RestClientResponseException`/`RestClientException` into `GitHubApiException`/`GitHubTransientApiException`, same header set, same `@Retryable` pattern as `fetchPullRequestFiles` for the transient case): `createCheckRun(repositoryFullName, headSha, installationAccessToken)` (called at `AnalysisRun` creation time — actually, more precisely, this should be triggered from the *existing* `AnalysisRunReadyForExecutionEvent` listener chain, creating a check run in `in_progress` state) and `updateCheckRun(repositoryFullName, checkRunId, conclusion, installationAccessToken)` (called from the same trigger point `ReportPublicationService` already uses: `VerdictProducedEvent`).
2. Add a new `CheckRunPublicationService` in `com.gatekeeper.orchestration`, structurally identical to `ReportPublicationService`: a thin listener method on `VerdictProducedEvent`, idempotent (skip if a check run already exists for this analysis run — likely needs a new nullable column, e.g. `analysis_runs.github_check_run_id`, to remember the check run's GitHub-side ID between the "create" and "update" calls), and deliberately swallowing its own exceptions the same way `ReportGenerationListener` does today, so a GitHub API failure here can never roll back or affect the `Verdict` that already committed.
3. Add the `checks:write` permission to the GitHub App's manifest (an operator/GitHub-settings action, not code) — existing installations will need to accept the updated permission set.

**Every file that will change:**

- `backend/src/main/java/com/gatekeeper/github/GitHubApiClient.java` — two new methods.
- `backend/src/main/java/com/gatekeeper/orchestration/CheckRunPublicationService.java` — new file.
- `backend/src/main/resources/db/migration/V11__analysis_run_check_run_id.sql` — new file, one nullable column.
- `backend/src/main/java/com/gatekeeper/analysisrun/AnalysisRun.java` — one new field for the check run ID.
- Test files: a new `CheckRunPublicationServiceTest.java` (unit, mocked collaborators, mirroring `GitHubInstallationServiceTest`'s style), plus additions to `GitHubApiClientTest.java` for the two new methods.

No existing file's *behavior* changes — this is purely additive, listening on an event (`VerdictProducedEvent`) that already exists and is already published at exactly the right moment.

**I have not written any of this code.** Per the task, this section describes the plan only, pending your approval to proceed into Phase 2 implementation.
