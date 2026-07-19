package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.verdict.dto.VerdictReasonSummary;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * verdictOutcome/verdictReasons were added additively (Sprint 5 Architecture,
 * Section 14 / ADR-043), the same way securityFindingsBySeverity was added
 * alongside findingsBySeverity. Both are null/empty for any run that has not
 * reached COMPLETED. verdictReasons is a lightweight inline projection (not
 * a second round-trip to GET /api/v1/verdicts/{id}) - a viewer of one
 * AnalysisRun's detail sees the full governance explanation immediately.
 * <p>
 * pullRequestId is added the same additive way, as a top-level sibling of the
 * existing nested pullRequest reference rather than a new field on
 * PullRequestReference itself - that record is also embedded in
 * ReportDetailResponse and constructed directly (not just via .from()) in an
 * existing test, and this codebase's established convention already favors a
 * small duplicated field over widening a shared reference type. It exists so
 * the frontend can link from a run's detail view to its Pull Request's own
 * detail view (GET /api/v1/pull-requests/{id}).
 */
public record AnalysisRunDetailResponse(
        Long id,
        Long pullRequestId,
        RepositoryReference repository,
        PullRequestReference pullRequest,
        String commitSha,
        AnalysisRunStatus status,
        AnalysisRunTriggerReason triggerReason,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Map<PolicySeverity, Long> findingsBySeverity,
        Map<SecuritySeverity, Long> securityFindingsBySeverity,
        VerdictOutcome verdictOutcome,
        List<VerdictReasonSummary> verdictReasons) {

    public static AnalysisRunDetailResponse from(
            AnalysisRun run,
            Map<PolicySeverity, Long> findingsBySeverity,
            Map<SecuritySeverity, Long> securityFindingsBySeverity,
            VerdictOutcome verdictOutcome,
            List<VerdictReasonSummary> verdictReasons) {
        return new AnalysisRunDetailResponse(
                run.getId(),
                run.getPullRequest().getId(),
                RepositoryReference.from(run.getPullRequest().getRepository()),
                PullRequestReference.from(run.getPullRequest()),
                run.getCommitSha(),
                run.getStatus(),
                run.getTriggerReason(),
                run.getFailureReason(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                findingsBySeverity,
                securityFindingsBySeverity,
                verdictOutcome,
                verdictReasons);
    }
}
