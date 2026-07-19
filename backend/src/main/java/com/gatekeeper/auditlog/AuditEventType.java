package com.gatekeeper.auditlog;

/**
 * Catalog of events {@link AuditLogService} can record. Grown one producer at
 * a time as each module actually adopts it (Milestone 1: ENGINEERING_REPORT_PUBLISHED
 * only). Milestone 7 (Enterprise Audit Logging) wires the remaining core
 * governance actions: review decisions, verdicts, policy configuration
 * changes, and repository/user/role lifecycle management - the exact set
 * docs/Product-Vision.md's audit requirement names ("who did what, when,
 * to which repository/pull request/analysis run").
 */
public enum AuditEventType {
    ENGINEERING_REPORT_PUBLISHED,
    VERDICT_PRODUCED,
    REVIEW_DECISION_RECORDED,
    POLICY_CONFIGURATION_CHANGED,
    REPOSITORY_CONNECTED,
    REPOSITORY_UPDATED,
    REPOSITORY_REMOVED,
    USER_CREATED,
    USER_UPDATED,
    USER_REMOVED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_REMOVED
}
