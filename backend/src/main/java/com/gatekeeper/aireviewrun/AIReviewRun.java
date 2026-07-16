package com.gatekeeper.aireviewrun;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records one AI Review Engine invocation for an AnalysisRun (Sprint 4
 * Milestone 3). Deliberately its own entity with its own lifecycle, not a
 * column on AnalysisRun itself and not folded into policy_findings/
 * security_findings: an AI review can fail independently of, and without
 * ever affecting, its parent AnalysisRun's own COMPLETED/FAILED transition
 * (Architecture.md Section 3 principle 5 / Section 11 - AI Review failures
 * must never stop the analysis pipeline or delay a governance decision).
 * <p>
 * Extends BaseEntity (unlike PolicyFindingEntity/SecurityFindingEntity,
 * which don't) because unlike a write-once finding, this row's status is
 * written exactly once but as a genuine outcome recorded after the fact -
 * mirroring AnalysisRun's own use of BaseEntity for the same auditing
 * columns, not because this row is ever updated post-insert.
 * <p>
 * provider/model/promptVersion are captured per row (not read from current
 * configuration when queried later) because configuration can change over
 * time - a row must record what was actually used to produce it.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_review_runs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIReviewRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AIReviewRunStatus status;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    /** Populated only when status is COMPLETED; null otherwise. */
    @Column(length = 2000)
    private String summary;

    /** Populated only when status is FAILED; null otherwise. */
    @Column(name = "failure_reason", length = 2000)
    private String failureReason;
}
