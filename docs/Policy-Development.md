# GateKeeper

# Policy Rule Development Guide

**Version:** 1.0
**Status:** Approved
**Document Owner:** GateKeeper Team
**Introduced:** Milestone 6 — Policy Management

---

# Purpose

This document is the canonical guide for writing a new `PolicyRule` and for understanding how organization-level configuration (Milestone 6) interacts with it. A future contributor should be able to add a rule correctly without reading `PolicyEngine`'s source.

---

# Why Rules Live in Code, Not the Database

A `PolicyRule` is a deterministic, unit-testable piece of logic - regex matching, structural checks, whatever a future rule needs. That logic belongs in code, reviewed like any other code, not in a database row that could encode arbitrary behavior nobody reviewed.

**Organizations configure rules. They never create rules.** The `policy_configurations` table (Milestone 6) stores exactly two things per organization per rule: whether it's enabled, and an optional severity override. It never stores a rule's matching logic, its id, its description, or its default category/severity - those always come from the live `PolicyRule` bean Spring discovers via classpath scan. `PolicyConfigurationController`'s catalog endpoint (`GET /api/v1/policies`) reads the rule list from the same `List<PolicyRule>` `PolicyEngine` holds, every time - there is no separate catalog to keep in sync, because there is only one source of truth.

This is also why there is no `POST /api/v1/policies`: you cannot create a rule through the API, only configure an existing one. Writing a new rule is always a code change (see below), reviewed and shipped like any other.

---

# How to Create a New PolicyRule

1. Create a class in `com.gatekeeper.policy.rule` implementing `PolicyRule` (or extending `CommentMarkerRule` if it's a simple single-marker text scan - see `TodoCommentRule`/`FixmeCommentRule` for the pattern).
2. Annotate it `@Component`. Spring's classpath scan does the rest - `PolicyEngine` requires zero code changes to pick it up.
3. Implement `id()`, `description()`, `defaultCategory()`, `defaultSeverity()`, and `evaluate(PolicyContext)`.
4. Keep it stateless: a single bean instance is shared across every concurrent `PolicyEngine.evaluate()` call. Instance fields must be immutable (e.g. a precompiled `Pattern` constant) or not exist.
5. `evaluate` must return an empty list (never `null`) when nothing is found, and must not throw for an expected "nothing to report" outcome - a thrown exception is treated as a rule bug, isolated from every other rule, not as "no findings."
6. Write a unit test against the rule directly (construct a `PolicyContext` literal - no Spring context, no database needed) and confirm `PolicyEngineIntegrationTest`'s discovery test still passes (it asserts the exact set of discovered rule ids, so a new rule needs adding there too, deliberately - the test is meant to name everything the engine actually runs).

---

# Rule ID Guidelines

**A rule's `id()` is a permanent product contract, not an implementation detail.**

- It is the key an organization's `PolicyConfiguration` override row is stored against (`policy_configurations.rule_id`). Once any organization has configured a rule, that id must never change - renaming it silently orphans every organization's enable/disable and severity-override settings for it, with no error and no migration path.
- Choose a short, stable, all-caps, underscore-separated identifier describing what is flagged, not how (`TODO_COMMENT`, not `REGEX_MARKER_SCAN_V1`).
- Never derive it from the class name or Spring bean name - both can be refactored freely; the id cannot.
- `PolicyEngine` fails fast at application startup if two discovered rules share an id (`IllegalStateException`) - this is a deployment-blocking configuration error, not something the engine silently works around, because a collision would make one rule's organization overrides silently apply to a different rule.

---

# Default Severity Philosophy

A rule's `defaultSeverity()` is what every organization gets until they explicitly override it via `PUT /api/v1/policies/{ruleId}`. Choose it based on the finding's inherent risk if left unresolved, using the existing four-level scale (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) consistently with rules that already exist:

- **LOW** - stylistic or planned-but-incomplete work (e.g. `TODO_COMMENT`: signals intent, not a defect).
- **MEDIUM** - a known, acknowledged problem left in place (e.g. `FIXME_COMMENT`: signals something is actually broken).
- **HIGH** / **CRITICAL** - reserved for findings an organization would reasonably want blocking merge on by default; no current Policy Engine rule defaults this high, but a future structural rule (e.g. a banned dangerous API call) reasonably could.

Remember that `VerdictEngine`'s `HighSeverityPolicyFindingRule` thresholds on this value - a default that's too high blocks merges by surprise; too low means an organization has to discover and manually escalate a rule that should have mattered from day one. When in doubt, default lower and let organizations opt into stricter enforcement via severity override, rather than the reverse.

---

# Default Category Philosophy

`PolicyCategory` groups findings by *what kind of concern* they represent, so findings can be filtered/reported on without parsing rule ids. It is **not** configurable by organizations in Milestone 6 (only enablement and severity are) - it's structural metadata about the rule itself, not a governance dial.

Use the existing categories where they genuinely fit (`MAINTAINABILITY` for incomplete-but-working code, `CODE_QUALITY` for known-defect markers); introduce a new `PolicyCategory` value only when a new rule doesn't conceptually belong to any existing one - adding a new enum value is a normal, low-risk code change, unlike adding a new rule id (see above).

---

# How Organization Overrides Work

Every evaluation loads one `PolicyConfigurationSet` - an immutable snapshot built fresh by `PolicyConfigurationService.buildConfigurationSet(organizationId)` from that organization's `policy_configurations` rows, with no row for a rule meaning "use its own default." `PolicyEngineService` passes this snapshot into `PolicyEngine.evaluate(context, configuration)` as a plain parameter; `PolicyEngine` itself never queries anything - it only asks the snapshot two questions per rule (`isEnabled`, `severityOverride`) and applies the answers:

- A disabled rule is skipped entirely - it does not run, and does not appear in `rulesEvaluated`.
- An overridden severity is applied to that rule's produced findings only, after `evaluate()` runs, without touching the rule's own logic or its `category`.

Because this snapshot is immutable and built once per evaluation, every finding produced by a single `evaluate()` call reflects exactly the configuration that existed at that moment - a configuration change made mid-evaluation (an unlikely but possible race) cannot produce a mix of old- and new-configuration findings within the same result.

**This is also why historical correctness holds without any extra bookkeeping**: `PolicyFindingEntity` rows are written once, at evaluation time, with whatever severity was effective then. A later configuration change never touches an already-persisted finding or a completed `AnalysisRun` - it only changes what the *next* evaluation sees. There is no "configuration version" stamped anywhere because none is needed: the finding's own persisted severity already *is* the permanent record of what configuration was in effect when it was produced.

---

# Guiding Principle

> A rule's logic is a code change. A rule's configuration is an organization's choice. Never confuse the two, and never let one leak into the other's storage.
