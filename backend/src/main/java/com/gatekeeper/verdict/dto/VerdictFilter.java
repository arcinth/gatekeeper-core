package com.gatekeeper.verdict.dto;

import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;

/** All fields optional - null means "not filtered on this criterion" (mirrors AIReviewRunFilter). */
public record VerdictFilter(
        Long analysisRunId,
        Long repositoryId,
        VerdictOutcome outcome,
        Instant from,
        Instant to) {
}
