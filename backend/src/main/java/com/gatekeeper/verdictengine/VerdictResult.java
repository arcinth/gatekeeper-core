package com.gatekeeper.verdictengine;

import java.time.Instant;
import java.util.List;

/**
 * The aggregated outcome of running every discovered VerdictRule against one
 * VerdictContext (Sprint 5 Architecture, Section 8). Success-shaped only,
 * exactly like PolicyResult/SecurityResult/AIReviewResult -
 * VerdictEngine#evaluate never returns a result representing its own
 * failure; if evaluation cannot produce a result, it throws (see
 * VerdictEngine's Javadoc).
 * <p>
 * reasons carries every VerdictReason collected from every rule, blocking or
 * not - even an APPROVED verdict retains its non-blocking reasons, so a
 * clean AnalysisRun's audit trail shows what was checked, not just what
 * wasn't found.
 *
 * @param analysisRunId identifies which AnalysisRun this verdict belongs to
 * @param outcome        the governance decision - BLOCKED if any reason is blocking, APPROVED otherwise
 * @param reasons        every VerdictReason collected from every rule, in rule-id order
 * @param evaluatedAt    when this verdict was produced
 */
public record VerdictResult(
        Long analysisRunId,
        VerdictOutcome outcome,
        List<VerdictReason> reasons,
        Instant evaluatedAt) {
}
