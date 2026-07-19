package com.gatekeeper.auditlog.dto;

import com.gatekeeper.auditlog.AuditEventType;
import java.time.Instant;

/**
 * Optional search criteria for {@code GET /api/v1/audit-logs} (Milestone 7:
 * Enterprise Audit Logging) - all fields null means "not filtered on this
 * criterion", mirroring PullRequestFilter/AnalysisRunFilter's convention.
 * Organization scoping is not a field here: it is always the caller's own
 * organization, applied separately by {@code AuditLogService} so it can
 * never be bypassed by a query parameter.
 */
public record AuditLogFilter(
        AuditEventType eventType,
        Long repositoryId,
        Long pullRequestId,
        Long analysisRunId,
        Long actorId,
        Instant occurredAfter,
        Instant occurredBefore) {
}
