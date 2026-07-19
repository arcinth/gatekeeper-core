package com.gatekeeper.reviewdecision.dto;

import com.gatekeeper.reviewdecision.ReviewDecision;
import com.gatekeeper.reviewdecision.ReviewDecisionType;
import java.time.Instant;

public record ReviewDecisionResponse(
        Long id,
        Long analysisRunId,
        ReviewDecisionType decision,
        String comment,
        Long reviewerId,
        String reviewerName,
        Instant createdAt) {

    public static ReviewDecisionResponse from(ReviewDecision reviewDecision) {
        return new ReviewDecisionResponse(
                reviewDecision.getId(),
                reviewDecision.getAnalysisRun().getId(),
                reviewDecision.getDecision(),
                reviewDecision.getComment(),
                reviewDecision.getReviewer().getId(),
                reviewDecision.getReviewer().getFullName(),
                reviewDecision.getCreatedAt());
    }
}
