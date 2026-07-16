package com.gatekeeper.auditlog;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.organization.Organization;
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
 * written, the entire point of an audit trail).
 * <p>
 * analysisRun is nullable, unlike EngineeringReport's required one: not
 * every future event type is analysis-run-scoped (e.g. "Repository
 * connected"), even though this milestone's only producer
 * (ReportPublicationService) always populates it.
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
    @JoinColumn(name = "analysis_run_id")
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(nullable = false, length = 1000)
    private String summary;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
