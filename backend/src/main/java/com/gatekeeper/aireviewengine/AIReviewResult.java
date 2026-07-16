package com.gatekeeper.aireviewengine;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of one AIReviewProvider call for one AIReviewContext.
 * Deliberately success-shaped only, mirroring PolicyResult/SecurityResult
 * exactly - this type never represents "the review failed," only "here's
 * what came back." Failure is the caller's concern: AIReviewProvider#review
 * throws on any failure rather than returning a result with an error state
 * baked in (Sprint 4 Architecture, Section 9).
 *
 * @param analysisRunId identifies which AnalysisRun this review belongs to
 * @param provider      the producing provider's AIReviewProvider#providerName(), for traceability
 * @param summary       an optional short narrative overview, distinct from the per-line findings
 * @param findings      the individual observations, normalized into the platform's own vocabulary
 * @param evaluatedAt   when this result was produced
 */
public record AIReviewResult(
        Long analysisRunId,
        String provider,
        String summary,
        List<AIReviewFinding> findings,
        Instant evaluatedAt) {

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
