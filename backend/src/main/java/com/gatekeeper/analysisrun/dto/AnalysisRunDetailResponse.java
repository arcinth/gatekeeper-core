package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.time.Instant;
import java.util.Map;

public record AnalysisRunDetailResponse(
        Long id,
        RepositoryReference repository,
        PullRequestReference pullRequest,
        String commitSha,
        AnalysisRunStatus status,
        AnalysisRunTriggerReason triggerReason,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Map<PolicySeverity, Long> findingsBySeverity,
        Map<SecuritySeverity, Long> securityFindingsBySeverity) {

    public static AnalysisRunDetailResponse from(
            AnalysisRun run,
            Map<PolicySeverity, Long> findingsBySeverity,
            Map<SecuritySeverity, Long> securityFindingsBySeverity) {
        return new AnalysisRunDetailResponse(
                run.getId(),
                RepositoryReference.from(run.getPullRequest().getRepository()),
                PullRequestReference.from(run.getPullRequest()),
                run.getCommitSha(),
                run.getStatus(),
                run.getTriggerReason(),
                run.getFailureReason(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                findingsBySeverity,
                securityFindingsBySeverity);
    }
}
