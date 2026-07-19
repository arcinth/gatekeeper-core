# GateKeeper

# Authorization Model

**Version:** 1.0
**Status:** Approved
**Document Owner:** GateKeeper Team
**Introduced:** Milestone 5 — RBAC Enforcement

---

# Purpose

This document is the canonical reference for how GateKeeper decides what an authenticated user is allowed to do. A future contributor should never need to read controller source code to understand the authorization model — this document, plus `RolePermissions.java` as its literal implementation, is the complete picture.

Before Milestone 5, authorization was a handful of ad hoc `@PreAuthorize` annotations hardcoding role names, present on only 3 of 14 controllers. Everything else was open to any authenticated user regardless of role — including submitting a governance review decision, the exact action the whole platform exists to gate. This document describes the model that replaced that.

---

# Authorization Philosophy

**Controllers express business capabilities. They never reference roles.**

A controller method asks one question: *"does this caller have permission X?"* It never asks *"is this caller an ADMINISTRATOR?"* Roles are an organizational concept (who someone is); permissions are an authorization concept (what an action requires). Conflating the two — checking role names directly in application code — is what Milestone 5 replaced, for two concrete reasons:

1. **It doesn't scale.** Every new role, or every change to what an existing role can do, would require hunting through every controller that happens to check that role's name. With permissions, a controller's authorization requirement is written once and never touched again, no matter how organizational roles evolve.
2. **It isn't explainable from one place.** With role names scattered across controllers, answering "what can a Technical Lead do?" required reading the entire codebase. With a centralized mapping, it's one table (below).

Role names exist in exactly three places in this codebase, and nowhere else:
- The `Role` entity/table (an organizational identity a user is assigned).
- `RolePermissions` (the one class that translates a role name into a set of permissions).
- This document.

If a role name ever appears in a controller, a service, or a `@PreAuthorize` expression outside `RolePermissions`, that is a bug against this architecture, not a style preference.

---

# Roles

Roles are seeded by `V1__init_schema.sql` and are a **manageable entity** — `RoleController` (itself gated by `ROLE_MANAGE`, see below) allows an administrator to create additional role names via the API. The six roles below are the well-known set this document's permission matrix covers; any other role name is handled by the deny-by-default rule described later.

| Role | Description |
|---|---|
| `ADMINISTRATOR` | Full platform control |
| `PLATFORM_ENGINEER` | Owns repository connections and participates in governance review |
| `DEVSECOPS_ENGINEER` | Participates in governance review, security-focused |
| `TECHNICAL_LEAD` | Participates in governance review |
| `ENGINEERING_MANAGER` | Participates in governance review |
| `DEVELOPER` | Read-only workspace access |

---

# Permissions

A small, fixed set of capabilities — one per *distinct action* the platform exposes today, not one per CRUD verb per domain. Defined in `com.gatekeeper.security.authorization.Permission`.

| Permission | Grants |
|---|---|
| `WORKSPACE_READ` | View Pull Requests, analysis runs, policy/security/AI findings, verdicts, engineering reports, the dashboard, repositories, and repository governance |
| `REVIEW_DECISION_CREATE` | Submit an APPROVE/REJECT review decision against an analysis run |
| `REPOSITORY_MANAGE` | Connect, update, or remove a repository |
| `USER_MANAGE` | Create, update, or remove users |
| `ROLE_MANAGE` | Create, update, or remove roles |

---

# Role → Permission Matrix

The single source of truth for this table is `RolePermissions.java`; `RolePermissionsTest.java` pins it exhaustively. If they ever disagree, the code is authoritative and this table is stale and must be corrected.

| Role | WORKSPACE_READ | REVIEW_DECISION_CREATE | REPOSITORY_MANAGE | USER_MANAGE | ROLE_MANAGE |
|---|:---:|:---:|:---:|:---:|:---:|
| ADMINISTRATOR | ✔ | ✔ | ✔ | ✔ | ✔ |
| PLATFORM_ENGINEER | ✔ | ✔ | ✔ | | |
| DEVSECOPS_ENGINEER | ✔ | ✔ | | | |
| TECHNICAL_LEAD | ✔ | ✔ | | | |
| ENGINEERING_MANAGER | ✔ | ✔ | | | |
| DEVELOPER | ✔ | | | | |
| *(any other role name)* | | | | | |

**Notable design choice:** a plain `DEVELOPER` can see everything (transparency is core to the product's value) but cannot submit a review decision. This closes the gap identified in the Product Readiness Review — previously, any authenticated user of any role could approve or reject a Pull Request.

---

# Default Deny Behavior

`RolePermissions.forRole(roleName)` never throws and never guesses. A role name that isn't one of the six rows above — including `null` — resolves to an **empty permission set**. That user is authenticated (they can log in, see their own profile), but every permission-gated action returns `403 Forbidden`.

This matters specifically because `Role` is a manageable entity: an administrator can create a brand-new role name today via `RoleController`. Under this model, that new role has **zero capabilities** until a developer explicitly adds a row to `RolePermissions` (or a future milestone builds a way for administrators to assign permissions to custom roles themselves — see below). This is a deliberate, disclosed trade-off: it is safer for a new role to be able to do nothing than to inherit unintended access.

---

# How to Add a Permission

1. Add the new constant to `Permission`.
2. Decide which existing roles should have it, and add it to their `Set.of(...)` entry in `RolePermissions`. Do not touch any controller.
3. Reference the new permission in exactly the controller(s) that need it, via `@PreAuthorize("hasAuthority('YOUR_NEW_PERMISSION')")`.
4. Update `RolePermissionsTest` and this document's matrix table together — they are two views of the same fact and must never drift.

# How to Add a Role

1. Add the constant to `RoleName` if it's a new well-known/built-in role (not a customer-created custom role — those go through `RoleController` and get no permissions until step 2 happens for them too).
2. Add its row to `RolePermissions`'s map and to this document's matrix table.
3. Nothing else changes. No controller is aware roles exist at all.

---

# Known Limitations (as of Milestone 5)

- **Static, in-code mapping.** `RolePermissions` is a compile-time table, not data in the database. A custom role created via `RoleController` gets no permissions until a developer updates the mapping in code. A future "permission assignment" capability (letting administrators configure a custom role's permissions through the API/UI) would extend this model without changing how any controller is annotated — controllers only ever reference `Permission`, never the mapping's storage mechanism.
- **Frontend is not permission-aware yet.** The UI does not hide actions a user cannot perform; a `DEVELOPER` still sees the "Submit Decision" form and receives a 403 from the backend on submit. This is deliberately out of scope for Milestone 5 and is expected to be addressed in a following, focused milestone.
- **No self-review restriction.** A user with `REVIEW_DECISION_CREATE` may approve their own Pull Request; this is an identity-based concern, distinct from role-based permission, and remains a deliberate scope exclusion (carried over from Milestone 2).
- **Single-tenant scope.** This model does not yet incorporate organization-scoped permissions (e.g., a permission granted only within one organization). It is intentionally shaped so that adding that later is additive to `RolePermissions`'s lookup key, not a rearchitecture of how controllers declare their requirements.
