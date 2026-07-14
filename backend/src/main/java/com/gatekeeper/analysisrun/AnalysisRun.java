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
 * Pull Request commit (docs/Database.md - Analysis Run entity). Analysis Runs
 * are immutable records once created - this class intentionally has no service
 * method that updates an existing row, only AnalysisRunService.createIfAbsent.
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
}
