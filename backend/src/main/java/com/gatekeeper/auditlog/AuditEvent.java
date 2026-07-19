package com.gatekeeper.auditlog;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * A governance action about to be recorded, passed into {@link AuditLogService#record}
 * (Milestone 7: Enterprise Audit Logging). Named for the event itself, not
 * "context" - every field here is either a fact about what happened
 * (eventType, actor, target, oldValue/newValue) or the scope it happened in
 * (organizationId, repositoryId, pullRequestId, analysisRunId), not
 * incidental request plumbing.
 * <p>
 * {@code summary} is a required, human-readable presentation string - but per
 * this milestone's explicit design direction, it is a presentation field, not
 * the durable "what changed" record. {@code oldValue}/{@code newValue} are
 * that durable record: small, JSON-serializable maps of the fields that
 * actually changed, or {@code null} when an event has no natural before/after
 * (e.g. a brand-new resource has no "old" state; an append-only event like a
 * review decision has no prior version being replaced).
 * <p>
 * {@code targetType}/{@code targetId} name what was acted on only when that
 * isn't already one of the dedicated scope ids above - see
 * {@link AuditTargetType}'s Javadoc.
 * <p>
 * {@code correlationId} is optional here: most callers omit it and let
 * {@link AuditLogService} fill it in from the current request (see
 * com.gatekeeper.config.CorrelationIdFilter), so that every audit event
 * produced while handling one HTTP request shares the same id. A caller only
 * needs to set it explicitly outside a request context (e.g. a scheduled
 * job) where no such ambient value exists.
 */
@Getter
@Builder
public class AuditEvent {

    private final AuditEventType eventType;
    private final Long organizationId;
    private final String summary;

    private final Long repositoryId;
    private final Long pullRequestId;
    private final Long analysisRunId;

    private final Long actorId;

    private final AuditTargetType targetType;
    private final String targetId;

    private final Map<String, Object> oldValue;
    private final Map<String, Object> newValue;

    private final String correlationId;
}
