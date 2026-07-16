package com.gatekeeper.aireviewrun.dto;

import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import java.time.Instant;

/** All fields optional - null means "not filtered on this criterion" (mirrors SecurityFindingFilter). */
public record AIReviewRunFilter(
        Long analysisRunId,
        Long repositoryId,
        AIReviewRunStatus status,
        String provider,
        Instant from,
        Instant to) {
}
