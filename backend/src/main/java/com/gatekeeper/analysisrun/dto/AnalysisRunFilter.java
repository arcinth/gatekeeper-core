package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import java.time.Instant;

/** All fields optional - null means "not filtered on this criterion" (Milestone 5 Architecture, Section 5). */
public record AnalysisRunFilter(
        Long repositoryId,
        AnalysisRunStatus status,
        AnalysisRunTriggerReason triggerReason,
        Instant from,
        Instant to) {
}
