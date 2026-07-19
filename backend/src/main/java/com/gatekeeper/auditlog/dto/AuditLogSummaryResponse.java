package com.gatekeeper.auditlog.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLog;
import com.gatekeeper.auditlog.AuditTargetType;
import java.time.Instant;
import java.util.Map;

/**
 * One row of the standalone Audit Log search API (Milestone 7: Enterprise
 * Audit Logging). Deliberately a separate DTO from {@code AuditLogResponse}
 * (the shape embedded in a Report Detail response) rather than widening that
 * one - see {@code AIReviewRunSummaryResponse}'s Javadoc for why this
 * codebase favors small, per-endpoint response DTOs over a shared one used
 * by an unrelated response shape. This response includes every scope/actor/
 * target dimension since, unlike the embedded shape, there is no parent
 * resource these are implied by.
 * <p>
 * {@code correlationId} is included here even though it isn't rendered by
 * the frontend table yet - it's harmless to expose and useful for anyone
 * calling this API directly.
 */
public record AuditLogSummaryResponse(
        Long id,
        AuditEventType eventType,
        String summary,
        Long organizationId,
        Long repositoryId,
        String repositoryFullName,
        Long pullRequestId,
        Integer pullRequestNumber,
        Long analysisRunId,
        Long actorId,
        String actorName,
        AuditTargetType targetType,
        String targetId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String correlationId,
        Instant occurredAt) {

    public static AuditLogSummaryResponse from(AuditLog entity, ObjectMapper objectMapper) {
        return new AuditLogSummaryResponse(
                entity.getId(),
                entity.getEventType(),
                entity.getSummary(),
                entity.getOrganization().getId(),
                entity.getRepository() == null ? null : entity.getRepository().getId(),
                entity.getRepository() == null ? null : entity.getRepository().getFullName(),
                entity.getPullRequest() == null ? null : entity.getPullRequest().getId(),
                entity.getPullRequest() == null ? null : entity.getPullRequest().getNumber(),
                entity.getAnalysisRun() == null ? null : entity.getAnalysisRun().getId(),
                entity.getActor() == null ? null : entity.getActor().getId(),
                entity.getActor() == null ? null : entity.getActor().getFullName(),
                entity.getTargetType(),
                entity.getTargetId(),
                toMap(entity.getOldValue(), objectMapper),
                toMap(entity.getNewValue(), objectMapper),
                entity.getCorrelationId(),
                entity.getOccurredAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(String json, ObjectMapper objectMapper) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
