package com.gatekeeper.securityengine;

import java.time.Instant;
import java.util.List;

/**
 * The aggregated outcome of running every discovered SecurityRule against one
 * SecurityContext. rulesEvaluated is recorded separately from findings.size()
 * so "zero findings" (clean code) stays distinguishable from "zero rules ran"
 * (a wiring problem) when this shows up in logs or, later, a report.
 */
public record SecurityResult(
        Long analysisRunId,
        List<SecurityFinding> findings,
        int rulesEvaluated,
        Instant evaluatedAt) {

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
