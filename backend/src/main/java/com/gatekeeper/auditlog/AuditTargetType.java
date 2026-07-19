package com.gatekeeper.auditlog;

/**
 * What kind of thing an audit event's {@code targetId} identifies (Milestone
 * 7: Enterprise Audit Logging). Populated only when the target isn't already
 * one of {@link AuditLog}'s own dedicated scope columns (repository,
 * pullRequest, analysisRun) - those three already answer "what was acted on"
 * for repository- and pipeline-scoped events, so recording them again here
 * would just duplicate the same fact. USER/ROLE/POLICY_RULE events have no
 * such dedicated column (a policy rule id isn't even a database row - see
 * docs/Policy-Development.md), so this generic pair is how they name their
 * target.
 */
public enum AuditTargetType {
    USER,
    ROLE,
    POLICY_RULE
}
