package com.gatekeeper.aireviewrun.dto;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import java.time.Instant;
import java.util.Map;

/**
 * Single-run detail response for GET /api/v1/ai-review-runs/{id} (Sprint 4
 * Milestone 4). findingsByConfidence mirrors AnalysisRunDetailResponse's
 * findingsBySeverity/securityFindingsBySeverity maps - a confidence
 * breakdown, not a severity one, since AI Review findings are advisory and
 * deliberately do not carry a severity (see AIReviewConfidence's Javadoc).
 */
public record AIReviewRunDetailResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        AIReviewRunStatus status,
        String provider,
        String model,
        String promptVersion,
        String summary,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Map<AIReviewConfidence, Long> findingsByConfidence) {

    public static AIReviewRunDetailResponse from(AIReviewRun run, Map<AIReviewConfidence, Long> findingsByConfidence) {
        return new AIReviewRunDetailResponse(
                run.getId(),
                run.getAnalysisRun().getId(),
                run.getAnalysisRun().getPullRequest().getRepository().getFullName(),
                run.getAnalysisRun().getPullRequest().getNumber(),
                run.getAnalysisRun().getCommitSha(),
                run.getStatus(),
                run.getProvider(),
                run.getModel(),
                run.getPromptVersion(),
                run.getSummary(),
                run.getFailureReason(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                findingsByConfidence);
    }
}
