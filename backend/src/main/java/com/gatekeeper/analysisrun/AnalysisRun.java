package com.gatekeeper.analysisrun;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.pullrequest.PullRequest;
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
 * Represents one execution of the GateKeeper analysis pipeline for a single
 * Pull Request commit (docs/Database.md - Analysis Run entity). "Immutable"
 * here means what's already been recorded never changes retroactively - a
 * COMPLETED run's findings and verdict are final. It does not mean the status
 * field is frozen at creation: status is this run's own lifecycle progression
 * (RECEIVED -> QUEUED -> IN_PROGRESS -> COMPLETED/FAILED, Milestone 4
 * Architecture, Section 5), tracked in real time by AnalysisRunService.
 */
@Getter
@Setter
@Entity
@Table(name = "analysis_runs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequest pullRequest;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 20)
    private AnalysisRunTriggerReason triggerReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnalysisRunStatus status = AnalysisRunStatus.RECEIVED;

    /** Populated only when status is FAILED; null otherwise. */
    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    /** GitHub's check run id, once GitHubCheckRunService has created one; null until then. */
    @Column(name = "github_check_run_id")
    private Long githubCheckRunId;
}
