package com.gatekeeper.aireviewrun.dto;

import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import java.time.Instant;

/**
 * List-view row for GET /api/v1/ai-review-runs (Sprint 4 Milestone 4).
 * Denormalizes repository/PR context onto the row itself, mirroring
 * SecurityFindingResponse's flat style rather than AnalysisRunDetailResponse's
 * nested Reference style - this codebase's established convention favors
 * duplication over a shared reference abstraction (see RepositoryReference/
 * PullRequestReference's single-consumer usage in analysisrun.dto only).
 * <p>
 * findingsTotal is populated by the service layer from a separate batched
 * count query over the page's ids, not by this factory method - see
 * AIReviewRunQueryService.findPage and AnalysisRunService.findSummaryPage's
 * identical precedent for why that count is deliberately not part of the
 * same filtered/paginated query.
 * <p>
 * summary/failureReason are deliberately omitted here (potentially long free
 * text) - they belong on the detail response only, mirroring how
 * AnalysisRunSummaryResponse omits failureReason.
 */
public record AIReviewRunSummaryResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        AIReviewRunStatus status,
        String provider,
        String model,
        String promptVersion,
        Instant createdAt,
        Instant updatedAt,
        long findingsTotal) {

    public static AIReviewRunSummaryResponse from(AIReviewRun run, long findingsTotal) {
        return new AIReviewRunSummaryResponse(
                run.getId(),
                run.getAnalysisRun().getId(),
                run.getAnalysisRun().getPullRequest().getRepository().getFullName(),
                run.getAnalysisRun().getPullRequest().getNumber(),
                run.getAnalysisRun().getCommitSha(),
                run.getStatus(),
                run.getProvider(),
                run.getModel(),
                run.getPromptVersion(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                findingsTotal);
    }
}
