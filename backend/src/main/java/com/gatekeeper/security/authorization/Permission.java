package com.gatekeeper.security.authorization;

/**
 * Every business capability the platform can gate (Milestone 5: RBAC
 * Enforcement). This is the only vocabulary a controller is allowed to speak
 * in - see this package's Javadoc and docs/Authorization-Model.md for the
 * full rule: controllers reference permissions, never role names.
 * <p>
 * Deliberately small and mapped to the actual distinct actions the platform
 * exposes today, not one permission per CRUD verb per domain - see
 * {@link RolePermissions} for how each is granted.
 */
public enum Permission {

    /** View Pull Requests, analysis runs, findings, verdicts, reports, the dashboard, repositories, and repository governance. */
    WORKSPACE_READ,

    /** Submit an APPROVE/REJECT review decision against an analysis run. */
    REVIEW_DECISION_CREATE,

    /** Connect, update, or remove a repository. */
    REPOSITORY_MANAGE,

    /** Enable/disable a policy rule or override its severity for the organization (Milestone 6). */
    POLICY_MANAGE,

    /** Create, update, or remove users. */
    USER_MANAGE,

    /** Create, update, or remove roles. */
    ROLE_MANAGE,

    /** View the organization's audit log (Milestone 7). */
    AUDIT_LOG_READ,
}
