package com.gatekeeper.verdict;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.verdictengine.VerdictOutcome;
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
 * Persisted form of the frozen VerdictResult record (Sprint 5 Milestone 1).
 * Write-once, like PolicyFindingEntity/SecurityFindingEntity - not extending
 * BaseEntity (no updated_at to justify: a Verdict is never re-evaluated or
 * edited once written, unlike AIReviewRun, which extends BaseEntity for the
 * same auditing columns AnalysisRun itself uses). See this entity's own
 * migration (V7__verdicts.sql) for why that divergence from AIReviewRun is
 * deliberate, not an inconsistency.
 * <p>
 * analysisRun carries a database-level UNIQUE constraint (Sprint 5
 * Architecture, ADR-039): exactly one Verdict per AnalysisRun, ever. This is
 * enforced here, not just assumed by call-site discipline - see
 * AnalysisResultPersistenceService's Javadoc for where the single write
 * happens.
 */
@Getter
@Setter
@Entity
@Table(name = "verdicts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Verdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false, unique = true)
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerdictOutcome outcome;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
