package com.gatekeeper.auditlog;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An immutable governance event record (Unified Engineering Report
 * Architecture, Section 8; docs/Database.md - "Audit Log": "Audit records
 * are immutable"). Write-once, like Verdict/EngineeringReport - not
 * extending BaseEntity (no updated_at: an audit entry is never edited once
 * written, the entire point of an audit trail). Keeps {@code @Setter} anyway,
 * matching Verdict/ReviewDecision's own precedent: immutability here is a
 * matter of discipline (no update endpoint exists, and no service ever calls
 * a setter after the initial {@link AuditLogRepository#save}), not of
 * removing Lombok-generated setters outright.
 * <p>
 * repository/pullRequest/analysisRun are all nullable and independently
 * populated depending on the event's own scope - a REPOSITORY_UPDATED event
 * has a repository but no pull request or analysis run; a
 * REVIEW_DECISION_RECORDED event has all three (derived from its analysis
 * run); a USER_UPDATED event has none of them.
 * <p>
 * actor is nullable: some events are system-produced with no human actor
 * (e.g. VERDICT_PRODUCED, ENGINEERING_REPORT_PUBLISHED).
 * <p>
 * targetType/targetId (see {@link AuditTargetType}) and oldValue/newValue
 * (Milestone 7 refinement: structured data is the primary source of truth,
 * {@link #summary} is a presentation field only) are the durable "what was
 * acted on" / "what changed" record. oldValue/newValue are pre-serialized
 * JSON strings (produced by {@link AuditLogService} from an
 * {@link AuditEvent}'s {@code Map<String, Object>} fields) - the same
 * "raw JSON in a plain column" convention {@code GitHubInstallation.permissions}
 * already established, not a new one.
 * <p>
 * correlationId ties together every audit event produced while handling one
 * HTTP request (see com.gatekeeper.config.CorrelationIdFilter) - not surfaced
 * in the UI yet, but present in the model for future request-tracing.
 * <p>
 * organization is required - every event belongs to the organization that
 * owns the repository/run it concerns (docs/Database.md ER diagram roots
 * Audit Log under the same tenant boundary as everything else).
 */
@Getter
@Setter
@Entity
@Table(name = "audit_logs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id")
    private PullRequest pullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_run_id")
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50)
    private AuditTargetType targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
