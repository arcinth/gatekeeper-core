package com.gatekeeper.aireviewfinding.dto;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import java.time.Instant;

/**
 * All fields optional - null means "not filtered on this criterion" (mirrors
 * SecurityFindingFilter). Supports filtering by both aiReviewRunId (the
 * finding's direct parent) and analysisRunId (its grandparent, reached via
 * aiReviewRun.analysisRun) - AIReviewFindingEntity is one indirection level
 * deeper than SecurityFindingEntity, so both levels are useful filter
 * anchors depending on whether the caller already has a specific
 * AIReviewRun or just an AnalysisRun in hand.
 */
public record AIReviewFindingFilter(
        Long aiReviewRunId,
        Long analysisRunId,
        Long repositoryId,
        AIReviewConfidence confidence,
        AIReviewFindingType type,
        Instant from,
        Instant to) {
}
