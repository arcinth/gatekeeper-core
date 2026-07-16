package com.gatekeeper.aireviewfinding.dto;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingEntity;
import com.gatekeeper.analysisrun.AnalysisRun;
import java.time.Instant;

/**
 * Cross-run listing row for GET /api/v1/ai-review-findings (Sprint 4
 * Milestone 4) - mirrors SecurityFindingResponse exactly, denormalizing
 * repository/PR context onto the finding itself so a flat, filterable list
 * doesn't require a second lookup. Deliberately exposes {@code confidence},
 * never a severity field - AI Review findings are advisory only and must be
 * visually/structurally distinguished from deterministic findings (see
 * AIReviewConfidence's Javadoc for why that distinction is load-bearing).
 * <p>
 * Carries both {@code aiReviewRunId} (its direct parent) and
 * {@code analysisRunId} (reached one hop further, through
 * {@code aiReviewRun.analysisRun}) - one indirection level deeper than
 * SecurityFindingResponse's single {@code analysisRunId}.
 */
public record AIReviewFindingResponse(
        Long id,
        Long aiReviewRunId,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        AIReviewFindingType type,
        AIReviewConfidence confidence,
        String filePath,
        Integer lineNumber,
        String message,
        String recommendation,
        Instant createdAt) {

    public static AIReviewFindingResponse from(AIReviewFindingEntity entity) {
        AnalysisRun analysisRun = entity.getAiReviewRun().getAnalysisRun();
        return new AIReviewFindingResponse(
                entity.getId(),
                entity.getAiReviewRun().getId(),
                analysisRun.getId(),
                analysisRun.getPullRequest().getRepository().getFullName(),
                analysisRun.getPullRequest().getNumber(),
                analysisRun.getCommitSha(),
                entity.getType(),
                entity.getConfidence(),
                entity.getFilePath(),
                entity.getLineNumber(),
                entity.getMessage(),
                entity.getRecommendation(),
                entity.getCreatedAt());
    }
}
