package com.gatekeeper.auditlog;

import com.gatekeeper.auditlog.dto.AuditLogFilter;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for {@code GET /api/v1/audit-logs}
 * (Milestone 7: Enterprise Audit Logging), mirroring
 * PullRequestSpecifications' shape: filters are optional and independently
 * combinable. Organization scoping is always applied and is never optional -
 * unlike every other filter here, it comes from the authenticated caller, not
 * a query parameter, so a caller can never see another organization's audit
 * trail.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    private static Specification<AuditLog> hasOrganizationId(Long organizationId) {
        return (root, query, builder) -> builder.equal(root.get("organization").get("id"), organizationId);
    }

    private static Specification<AuditLog> hasEventType(AuditEventType eventType) {
        return (root, query, builder) -> eventType == null ? null : builder.equal(root.get("eventType"), eventType);
    }

    private static Specification<AuditLog> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("repository").get("id"), repositoryId);
    }

    private static Specification<AuditLog> hasPullRequestId(Long pullRequestId) {
        return (root, query, builder) -> pullRequestId == null ? null
                : builder.equal(root.get("pullRequest").get("id"), pullRequestId);
    }

    private static Specification<AuditLog> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("analysisRun").get("id"), analysisRunId);
    }

    private static Specification<AuditLog> hasActorId(Long actorId) {
        return (root, query, builder) -> actorId == null ? null
                : builder.equal(root.get("actor").get("id"), actorId);
    }

    private static Specification<AuditLog> occurredAfter(java.time.Instant occurredAfter) {
        return (root, query, builder) -> occurredAfter == null ? null
                : builder.greaterThanOrEqualTo(root.get("occurredAt"), occurredAfter);
    }

    private static Specification<AuditLog> occurredBefore(java.time.Instant occurredBefore) {
        return (root, query, builder) -> occurredBefore == null ? null
                : builder.lessThanOrEqualTo(root.get("occurredAt"), occurredBefore);
    }

    public static Specification<AuditLog> matching(Long organizationId, AuditLogFilter filter) {
        return Specification.where(hasOrganizationId(organizationId))
                .and(hasEventType(filter.eventType()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasPullRequestId(filter.pullRequestId()))
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasActorId(filter.actorId()))
                .and(occurredAfter(filter.occurredAfter()))
                .and(occurredBefore(filter.occurredBefore()));
    }
}
