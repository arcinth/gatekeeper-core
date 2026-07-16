package com.gatekeeper.report;

import com.gatekeeper.analysisrun.AnalysisRun;
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
 * Records the fact and moment a Unified Engineering Report was published for
 * an AnalysisRun (Unified Engineering Report Architecture, Section 5).
 * Deliberately a thin publication marker, not a data warehouse: it holds no
 * copy of Policy/Security/AI findings or the Verdict itself - those are
 * assembled at read time from their own system of record (ReportQueryService,
 * Milestone 2), the same "no duplication, cross-reference by
 * analysis_run_id" precedent VerdictReason already established for
 * Policy/Security findings (ADR-040), now extended to the report as a whole
 * (ADR-044).
 * <p>
 * Write-once, like Verdict/VerdictReasonEntity - not extending BaseEntity
 * (no updated_at to justify: a report is never re-published or edited once
 * written).
 * <p>
 * analysisRun carries a database-level UNIQUE constraint (V8__engineering_reports.sql):
 * exactly one EngineeringReport per AnalysisRun, ever. ReportPublicationService
 * relies on this constraint to resolve the race between its two independent
 * trigger paths - a Verdict being produced, and AI review finishing - safely,
 * without a distributed lock (ADR-045).
 */
@Getter
@Setter
@Entity
@Table(name = "engineering_reports")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngineeringReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false, unique = true)
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_review_status", nullable = false, length = 20)
    private AiReviewStatus aiReviewStatus;

    @CreationTimestamp
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;
}
