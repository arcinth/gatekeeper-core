# GateKeeper — Architecture

**Version:** 1.0
**Status:** Frozen (Pre-Implementation)
**Document Owner:** GateKeeper Team
**Source:** [Product-Vision.md](./Product-Vision.md) (Approved), [Product-Backlog.md](./Product-Backlog.md) (Approved)

---

## 1. Purpose

This document defines the frozen architecture of GateKeeper prior to implementation. It refines the architecture established during the initial architecture brainstorming session by introducing two structural abstractions — a common **Analysis Engine** interface and a common **Finding** model — without altering any decision already made about component boundaries, responsibilities, or product philosophy.

This document is the authoritative architectural reference for implementation. Any deviation from it requires a new Architecture Decision Record (ADR), not a silent code change.

---

## 2. High-Level Architecture

GateKeeper is organized into six architectural layers, each with a single reason to change:

- **Integration Layer** — the only layer permitted to communicate with external systems (GitHub, AI provider).
- **Orchestration Layer** — coordinates the analysis pipeline for a Pull Request, depending only on abstractions, never on concrete engine implementations.
- **Engine Layer** — a family of interchangeable analysis engines (Policy, Security, AI Review, and future engines), each implementing the common `AnalysisEngine` abstraction.
- **Decision Layer** — the Verdict Engine, the sole authority over governance decisions.
- **Persistence Layer** — the system of record for repositories, Pull Requests, findings, verdicts, reports, and audit history.
- **Presentation & Access Layer** — the Engineering Dashboard, Auth, and RBAC, through which humans observe and govern access to the system.

These layers are strictly one-directional in dependency: Integration → Orchestration → Engine → Decision → Persistence, with the Presentation & Access Layer reading from Persistence and issuing commands through Orchestration. No engine depends on another engine. No layer bypasses the layer below it.

---

## 3. Architectural Principles

1. **Policy-First, AI-Assisted.** Deterministic engines establish truth. AI provides judgment. This principle governs every architectural decision in this document.
2. **Single Integration Points.** Exactly one module speaks to GitHub. Exactly one module speaks to the AI provider. External dependencies are never scattered across the codebase.
3. **Program to Abstractions.** The Orchestration Layer and Report Engine depend on the `AnalysisEngine` and `Finding` abstractions, never on concrete engine or finding types. This is what allows GateKeeper to add engines without modifying orchestration logic.
4. **Deterministic Authority.** Only deterministic findings may influence a governance decision. Advisory findings may never influence a governance decision, regardless of their source or confidence.
5. **Failure Isolation.** The unavailability of any single engine — particularly the AI Review Engine — must never stop the analysis pipeline or delay a governance decision.
6. **Auditability by Design.** Every governance decision must be explainable and traceable after the fact.
7. **Modularity.** Each backend module has one responsibility and one reason to change. Modules communicate through well-defined contracts, not shared internal state.

---

## 4. Major Components

- GitHub Integration Module
- Auth & RBAC Subsystem
- Repository Management Module
- Analysis Orchestration Layer
- Analysis Engine family (Policy Engine, Security Engine, AI Review Engine), unified under the `AnalysisEngine` abstraction
- Verdict Engine
- Report Engine
- Persistence Layer (including Audit Log)
- Engineering Dashboard (API + UI)

This list is unchanged from the initial brainstorming session. The refinements in this document affect how the Engine family and their outputs are modeled internally — not which components exist.

---

## 5. Backend Modules

- `auth-module` — authentication, session handling
- `rbac-module` — roles, permissions, enforcement
- `repository-module` — connect, list, and remove repositories
- `github-integration-module` — the only module permitted to receive GitHub webhooks or call the GitHub API
- `orchestration-module` — sequences Pull Request analysis; depends only on the `AnalysisEngine` abstraction
- `policy-engine-module` — implements `AnalysisEngine`; deterministic
- `security-engine-module` — implements `AnalysisEngine`; deterministic
- `ai-review-engine-module` — implements `AnalysisEngine`; advisory; the only module permitted to call the AI provider
- `verdict-engine-module` — consumes deterministic `Finding`s only; sole owner of the governance decision
- `report-engine-module` — consumes the full `Finding` collection, regardless of type, to compile the Unified Engineering Report
- `dashboard-api-module` — read-side aggregation for the dashboard
- `shared-domain-module` — hosts the `AnalysisEngine` and `Finding` abstractions and any contracts shared across modules; no engine module depends on another engine module

---

## 6. Complete Pull Request Lifecycle

1. GitHub sends a webhook (Pull Request opened or synchronized). The `github-integration-module` receives it and verifies its authenticity.
2. `orchestration-module` creates an `AnalysisRun` — the execution context for that Pull Request's analysis.
3. `github-integration-module` fetches the Pull Request's metadata and diff from the GitHub API.
4. `orchestration-module` invokes each registered `AnalysisEngine` implementation in turn, passing the same `AnalysisRun` context to each.
5. Each engine (Policy, Security, AI Review) independently produces a collection of `Finding`s, tagged as Deterministic or Advisory according to its own nature.
6. All `Finding`s are persisted against the `AnalysisRun`.
7. `verdict-engine-module` reads only the Deterministic `Finding`s (Policy + Security) and produces the governance decision.
8. `report-engine-module` reads the full `Finding` collection (Deterministic and Advisory) and compiles the Unified Engineering Report.
9. `github-integration-module` posts the governance decision and report summary back to the Pull Request (status check and/or comment).
10. `dashboard-api-module` reflects the updated state for the organization.
11. The developer resolves findings and pushes new commits, re-triggering the webhook and restarting the cycle from step 1. Merging itself remains a GitHub/human action; GateKeeper never merges automatically.

---

## 7. Analysis Pipeline

The pipeline is orchestrated, not hard-coded. `orchestration-module` holds an ordered collection of `AnalysisEngine` instances and invokes each one polymorphically against the current `AnalysisRun`. The orchestrator does not know, and does not need to know, whether it is invoking the Policy Engine, the Security Engine, the AI Review Engine, or any future engine — it only knows it is invoking something that satisfies the `AnalysisEngine` contract.

This makes the pipeline:

- **Order-independent for correctness** — each engine operates on the same immutable Pull Request snapshot and does not depend on another engine's output.
- **Fault-isolated** — a failure in one engine invocation is caught at the orchestration boundary and does not propagate to other engines.
- **Extensible** — adding a new engine is a registration change, not a pipeline rewrite.

---

## 8. Analysis Engine Abstraction

Every analysis engine — Policy, Security, AI Review, and any future engine (Architecture, Performance, Compliance, Secrets, etc.) — implements a single common abstraction, conceptually:

- `AnalysisEngine`
  - `PolicyEngine`
  - `SecurityEngine`
  - `AIReviewEngine`
  - *(future: ArchitectureEngine, PerformanceEngine, ComplianceEngine, SecretsEngine, …)*

Each engine, through this abstraction, is responsible for:

- Accepting an `AnalysisRun` as its unit of work
- Producing a collection of `Finding`s
- Declaring whether its findings are Deterministic or Advisory in nature

The Orchestration Layer depends exclusively on this abstraction. It has no compile-time or runtime knowledge of any specific engine's internals. This is the mechanism that satisfies the Product Vision's requirement that "future versions will introduce additional analysis engines without changing the overall platform architecture" — new engines are added by registering a new implementation, not by modifying `orchestration-module`.

This abstraction does not weaken the Deterministic/Advisory distinction — it formalizes it. Whether an engine's output can influence a merge decision is a property of the engine (and of the `Finding`s it produces), enforced downstream by the Verdict Engine, not by orchestration logic.

---

## 9. Finding Model

Instead of Policy, Security, and AI findings being treated as unrelated concepts, they share a common parent abstraction:

- `Finding`
  - `PolicyFinding`
  - `SecurityFinding`
  - `AIFinding`

Every `Finding`, regardless of subtype, is associated with the `AnalysisRun` it belongs to and carries a classification of **Deterministic** or **Advisory**. `PolicyFinding` and `SecurityFinding` are always Deterministic. `AIFinding` is always Advisory. This classification is intrinsic to the type, not a configurable flag — it is what allows downstream consumers to reason about a `Finding` safely without knowing its concrete subtype.

Two downstream consumers read this model differently, by design:

- **Report Engine** consumes the entire `Finding` collection, regardless of subtype. Its responsibility is to present a complete, unified picture of everything every engine observed.
- **Verdict Engine** consumes only the subset of `Finding`s classified as Deterministic. It has no code path capable of reading an `AIFinding` for decision purposes — this is an architectural guarantee, not a runtime check.

This shared abstraction is what allows the Report Engine to remain engine-agnostic (it does not need a new code path every time a new engine is added) while keeping the Verdict Engine's authority strictly bounded to deterministic output.

---

## 10. Verdict Flow

1. `verdict-engine-module` is invoked once all engines registered for the current pipeline have completed (or failed gracefully).
2. It reads only `Finding`s classified as Deterministic — in the MVP, this means `PolicyFinding` and `SecurityFinding`.
3. It aggregates these findings into a single governance decision for the `AnalysisRun`.
4. `AIFinding`s are never passed to, or evaluated by, the Verdict Engine. They travel a separate path directly to the Report Engine.
5. The governance decision produced by the Verdict Engine is final and authoritative for that analysis cycle; it is what `github-integration-module` reports back to GitHub as the Pull Request's status.

This flow is a direct, structural enforcement of the principle: **AI findings must never participate in merge decisions.**

---

## 11. AI Failure & Graceful Degradation

Because the AI Review Engine is invoked through the same `AnalysisEngine` abstraction as every other engine, its failure is handled uniformly by the orchestrator:

- `orchestration-module` invokes each engine with a bounded timeout and failure boundary.
- If the AI Review Engine fails, times out, or the AI provider is unavailable, the orchestrator records that engine's invocation as failed and continues the pipeline unaffected.
- Because the Policy Engine and Security Engine are separate `AnalysisEngine` instances invoked independently, their `Finding`s are unaffected by an AI Review Engine failure.
- The Verdict Engine never depended on `AIFinding`s in the first place, so the governance decision is produced normally.
- The Report Engine marks the AI section of the Unified Engineering Report as unavailable rather than omitting it silently, so developers know advisory feedback was skipped rather than clean.
- No retry of the AI Review Engine blocks the pipeline; the governance decision and GitHub status post proceed on schedule.

The `AnalysisEngine` abstraction is what makes this degradation mechanical rather than a special case: any future engine that fails is handled by the same fault boundary, with the same guarantee that deterministic governance continues uninterrupted.

---

## 12. Database Entity Overview (Entities Only)

- **User**
- **Role**
- **Organization**
- **Repository**
- **PullRequest**
- **AnalysisRun**
- **PolicyDefinition**
- **Finding** (with `PolicyFinding`, `SecurityFinding`, `AIFinding` as subtypes)
- **Verdict**
- **EngineeringReport**
- **AuditLog**

No columns, keys, or storage details are defined at this stage.

---

## 13. REST API Groups (High Level)

- **Auth API**
- **User & Role API**
- **Repository API**
- **Webhook API** (inbound GitHub events only)
- **Pull Request & Analysis API**
- **Report API**
- **Policy API**
- **Dashboard API**

No individual endpoints are defined at this stage.

---

## 14. Deployment View

GateKeeper is deployed as a modular backend application (organized by the modules in Section 5) alongside a persistence store and a frontend dashboard application. The backend exposes:

- An inbound webhook endpoint reachable by GitHub.
- Outbound connectivity to the GitHub API (via `github-integration-module`) and to the configured AI provider (via `ai-review-engine-module`) — the only two permitted outbound external dependencies.
- A REST API surface consumed by the Engineering Dashboard and any future clients.

The Engine Layer is deployed as part of the same backend process for the MVP; its abstraction-based design (Section 8) means individual engines can be extracted into independently deployed services later without changing how the Orchestration Layer or Report Engine consume them.

---

## 15. Scalability Considerations

- **Engine independence enables parallelism.** Because engines do not depend on one another's output, `orchestration-module` may invoke Policy, Security, and AI Review concurrently rather than sequentially as load grows.
- **Stateless engine execution.** Each `AnalysisEngine` invocation operates on a single `AnalysisRun` with no shared mutable state, allowing horizontal scaling of engine execution.
- **Isolated external dependencies.** Because GitHub and AI provider communication are each isolated to a single module, rate limits, backoff, and quota management are centralized rather than duplicated.
- **Read/write separation for the dashboard.** `dashboard-api-module` reads from persisted, already-computed state (Findings, Verdicts, Reports) rather than recomputing analysis, keeping dashboard load decoupled from pipeline load.
- **Extensibility without re-architecture.** Because future engines register against the same `AnalysisEngine` abstraction, scaling the platform to more analysis types does not require orchestration or Verdict Engine changes.

---

## 16. Security Considerations

- **Single, auditable GitHub trust boundary.** All GitHub credentials, tokens, and webhook secrets are confined to `github-integration-module`, minimizing the surface where GitHub access could be misused.
- **Single, auditable AI provider trust boundary.** All AI provider credentials are confined to `ai-review-engine-module`; no other module can leak Pull Request content to an external AI service.
- **RBAC enforced at the access boundary.** Every request into the platform passes through `rbac-module` before reaching orchestration, repository, or dashboard operations.
- **Deterministic governance cannot be bypassed by AI compromise.** Because the Verdict Engine structurally cannot read `AIFinding`s, a compromised or manipulated AI provider cannot forge a merge decision.
- **Full auditability.** Every `AnalysisRun`, `Finding`, and `Verdict` is persisted and linked through `AuditLog`, so governance decisions can be reconstructed and explained after the fact.
- **Webhook authenticity.** All inbound GitHub webhooks are signature-verified by `github-integration-module` before any `AnalysisRun` is created.

---

## 17. Future Extensibility

The `AnalysisEngine` and `Finding` abstractions are the platform's primary extensibility mechanism. Adding a future capability from the Product Vision's Future Scope — Architecture Engine, Performance Analysis Engine, Compliance Engine, Secrets Engine, or others — requires only:

1. A new implementation of `AnalysisEngine`.
2. A new `Finding` subtype (if the engine's output doesn't fit existing subtypes), correctly classified as Deterministic or Advisory.
3. Registration of the new engine with `orchestration-module`.

No change is required to `orchestration-module`'s internal logic, `verdict-engine-module`'s decision logic, or `report-engine-module`'s report compilation logic. This is the architectural payoff of Improvements 1 and 2: the platform grows by addition, not modification.

Other future capabilities noted in the Product Vision and Product Backlog — additional source control integrations, a Custom Rule SDK, a Plugin Framework, Multi-Organization support, and Engineering Analytics — extend the Integration Layer, Repository Management, and Dashboard components respectively, and do not require changes to the Engine or Decision layers.

---

## 18. Observability (Cross-Cutting)

Observability (Milestone 9) is deliberately **not** a seventh architectural layer. It does not sit in the Integration → Orchestration → Engine → Decision → Persistence dependency chain defined in Section 2, and no existing layer depends on it for correctness — a layer can be understood and could theoretically run correctly with observability removed entirely. Instead, it wraps every layer uniformly: health, metrics, structured logs, performance timing, and error monitoring are added around existing components without altering any component's responsibilities, dependencies, or contracts.

- **Health, metrics, and application info** are exposed by Spring Boot Actuator/Micrometer on a separate management port — an operational surface, not a REST API business capability, and therefore not part of the layer chain in Section 2 or the API Groups in Section 13.
- **Structured logging** (correlation id, request id, actor identity) is carried through SLF4J's MDC, propagated across `@Async` boundaries — an orthogonal concern threaded through every layer's existing request/event handling, not a new dependency any layer takes on another.
- **Performance monitoring** (`@ObservedOperation`) and **error monitoring** (extending the existing `GlobalExceptionHandler`) observe calls into the Engine Layer and Integration Layer from the outside; neither changes what those layers compute or return.

Full detail — the metrics catalog, health indicator behavior, logging fields, and the management-port trust model — lives in [Observability.md](./Observability.md), not in this document, consistent with this document staying at the level of layers, boundaries, and decisions rather than an operational reference.

---

## 19. Security Hardening (Cross-Cutting)

Like Observability (Section 18), Security Hardening (Milestone 10) is not a new architectural layer - it wraps every layer without any layer taking on a new dependency because of it.

- **HTTP security headers** and **CORS tightening** apply at the same `SecurityConfig` filter-chain boundary every request already passes through - additive configuration, not a new component.
- **Rate limiting** (`com.gatekeeper.security.ratelimit`) sits in front of a small, named set of endpoints (login, refresh, the GitHub webhook, repository sync) as an explicit check each controller calls before its existing business logic runs - a request that passes the check reaches exactly the same authentication/authorization/business code that existed before this milestone.
- **JWT hardening** (issuer validation, clock skew, reuse detection) is a set of targeted edits inside the existing `JwtService`/`AuthService`, preserving every method signature and the token format itself.
- **Secrets validation** extends the existing `@Profile("prod")` startup-validator pattern from Milestones 1-9 with two additional, shared checks - no new validator classes, no new bean type.
- **Dependency/secret scanning** and **Docker hardening** are CI and container-runtime concerns with no runtime application dependency at all.

Full detail - the header reference, rate-limit thresholds, JWT hardening specifics, secrets policy, CI scanning tools, and Docker changes - lives in [Security-Hardening.md](./Security-Hardening.md), consistent with this document staying at the level of layers, boundaries, and decisions.

---

## 20. Architecture Decision Records (ADR)

### ADR-001: GitHub communication is isolated to a dedicated integration module

**Status:** Accepted

**Context:** GateKeeper must receive Pull Request events and report governance outcomes back to GitHub. Multiple modules could plausibly need GitHub access (repository management, orchestration, reporting).

**Decision:** All inbound webhook handling and outbound GitHub API calls are confined to `github-integration-module`. No other module communicates with GitHub directly.

**Consequences:** GitHub credentials, rate limits, and webhook verification are centralized, simplifying security review and failure handling. Any future change to GitHub's API or webhook contract touches exactly one module.

---

### ADR-002: AI is advisory only and cannot influence merge decisions

**Status:** Accepted

**Context:** GateKeeper's product philosophy is Policy-First, AI-Assisted. AI-generated findings are probabilistic and must not be allowed to determine whether code reaches production.

**Decision:** `AIFinding`s are structurally excluded from the Verdict Engine's decision logic. The Verdict Engine only reads Deterministic `Finding`s.

**Consequences:** Governance decisions remain predictable, explainable, and auditable regardless of AI provider behavior, cost, or availability. AI can be improved, swapped, or temporarily disabled without any risk to governance integrity.

---

### ADR-003: All analysis engines implement a common AnalysisEngine abstraction

**Status:** Accepted

**Context:** The MVP ships three engines (Policy, Security, AI Review), but the Product Vision explicitly anticipates future engines (Architecture, Performance, Compliance) without platform re-architecture.

**Decision:** Every analysis engine implements a common `AnalysisEngine` abstraction. `orchestration-module` depends only on this abstraction, never on concrete engine types.

**Consequences:** New engines are added by implementation and registration, not by modifying orchestration logic. This satisfies the Product Vision's extensibility requirement directly and reduces the risk of regressions when the engine family grows.

---

### ADR-004: All findings inherit from a common Finding abstraction

**Status:** Accepted

**Context:** Treating `PolicyFinding`, `SecurityFinding`, and `AIFinding` as unrelated types would force the Report Engine to special-case every engine's output, and would make it easy to accidentally leak an `AIFinding` into a decision path.

**Decision:** All findings inherit from a common `Finding` abstraction, carrying an intrinsic Deterministic/Advisory classification. The Report Engine consumes the full `Finding` collection polymorphically; the Verdict Engine consumes only the Deterministic subset.

**Consequences:** The Report Engine remains engine-agnostic as new engines are added. The Deterministic/Advisory boundary becomes a structural property of the type system rather than a convention that could be violated by mistake.

---

### ADR-005: Deterministic engines establish truth; AI provides judgment

**Status:** Accepted

**Context:** This is the foundational product philosophy stated in the Product Vision and must be reflected as an enforceable architectural rule, not just a stated intention.

**Decision:** Every architectural boundary in this document — the Verdict Engine's input restriction, the Finding classification model, and the AI failure handling strategy — is derived from and subordinate to this principle.

**Consequences:** Any future proposal that would allow an Advisory finding to influence a governance decision is, by definition, a violation of this ADR and must be rejected or must supersede it explicitly through a new ADR with full team review.

---

### ADR-006: AnalysisRun is the execution context for every Pull Request analysis

**Status:** Accepted

**Context:** Each engine invocation, and every Finding it produces, needs a shared unit of work to be associated with, tracked, and later audited.

**Decision:** `AnalysisRun` is created once per Pull Request analysis cycle and passed to every `AnalysisEngine` invocation. All `Finding`s, the `Verdict`, and the `EngineeringReport` are associated with a specific `AnalysisRun`.

**Consequences:** The full history of a Pull Request's governance evaluations is reconstructable run-by-run, which underpins both the Dashboard's status tracking and the Audit Log's traceability guarantees.

---

### ADR-007: Audit Log is a first-class architectural component

**Status:** Accepted

**Context:** Enterprise, financial, and healthcare target organizations require governance decisions to be explainable and reconstructable after the fact, not just correct at decision time.

**Decision:** `AuditLog` is retained as a persisted entity, linked to `AnalysisRun`, `Finding`, and `Verdict` records, rather than treated as incidental logging.

**Consequences:** Governance decisions can be audited and defended after the fact, satisfying the "predictable, explainable, and auditable" requirement from the Product Vision.

---

### ADR-008: Observability endpoints are isolated by a separate network port, not RBAC

**Status:** Accepted

**Context:** Milestone 9 needed a way for infrastructure tooling (Prometheus, container orchestrators) to reach health/metrics endpoints. Prometheus and orchestrator probes have no natural way to hold a GateKeeper JWT, so gating these endpoints behind the existing RBAC model would require inventing a permission (`SYSTEM_MONITOR`) that exists solely to be handed to infrastructure, not to a person or a real business role.

**Decision:** Actuator endpoints (health, info, metrics, prometheus, startup) are served on a separate `management.server.port`, isolated from the public API port by network configuration rather than by an application-level credential — the same trust boundary already accepted for PostgreSQL's own port in this project. An explicit endpoint allowlist (`env`, `beans`, `configprops`, `heapdump`, `threaddump`, `shutdown` never included) provides defense in depth on top of that isolation.

**Consequences:** Standard infrastructure tooling can scrape GateKeeper without any GateKeeper-specific credential, and RBAC's permission set stays scoped to genuine business capabilities rather than growing an entry for machine-only access. A deployment that fails to keep the management port off the public network relies on the endpoint allowlist as its only remaining defense — operators must not publish this port on a public load balancer or ingress (see [Observability.md](./Observability.md)).

---

### ADR-009: Rate limiting is in-memory and per-instance, behind a swappable abstraction

**Status:** Accepted

**Context:** Milestone 10 needed to close a real gap - login, refresh, and the GitHub webhook endpoint had no rate limiting at all. GateKeeper does not otherwise depend on Redis or any other shared-state infrastructure, and introducing one solely for rate limiting would be a larger infrastructure commitment than "harden what exists" called for, and would work against the "deployable on any infrastructure" requirement.

**Decision:** Rate limiting is implemented with Bucket4j entirely in-memory, behind a `RateLimiter` interface (`com.gatekeeper.security.ratelimit`) that callers depend on instead of Bucket4j directly. `InMemoryRateLimiter` is the only implementation.

**Consequences:** GateKeeper gains real protection against brute force, credential stuffing, and webhook flooding without a new infrastructure dependency, at the accepted cost that limits are enforced per-instance rather than cluster-wide (a 3-instance deployment effectively multiplies each limit by up to 3 in the worst case). A future Redis-backed implementation for horizontal scaling is a new class implementing the same `RateLimiter` interface, not a change to `RateLimitService` or any controller.
