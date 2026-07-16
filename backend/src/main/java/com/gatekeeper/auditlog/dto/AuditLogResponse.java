package com.gatekeeper.auditlog.dto;

import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLog;
import java.time.Instant;

/**
 * One audit trail entry embedded in a Report Detail response (Unified
 * Engineering Report Architecture, Section 9). Omits analysisRunId and
 * organizationId - both are already implied by the parent report this entry
 * is embedded under, the same "no redundant parent id" convention
 * VerdictReasonSummary already established for verdictId.
 */
public record AuditLogResponse(Long id, AuditEventType eventType, String summary, Instant occurredAt) {

    public static AuditLogResponse from(AuditLog entity) {
        return new AuditLogResponse(entity.getId(), entity.getEventType(), entity.getSummary(), entity.getOccurredAt());
    }
}
