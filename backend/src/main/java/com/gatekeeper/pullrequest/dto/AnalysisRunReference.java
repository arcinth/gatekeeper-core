package com.gatekeeper.pullrequest.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;

/**
 * One row in a Pull Request's analysis-run history (GET
 * /api/v1/pull-requests/{id}), newest first. verdictOutcome is populated by
 * the service layer from a separate batched query over the returned runs'
 * ids, not by this factory method - the same enrichment shape
 * AnalysisRunService.findSummaryPage already established.
 */
public record AnalysisRunReference(
        Long id,
        String commitSha,
        AnalysisRunStatus status,
        AnalysisRunTriggerReason triggerReason,
        VerdictOutcome verdictOutcome,
        Instant createdAt) {

    public static AnalysisRunReference from(AnalysisRun run, VerdictOutcome verdictOutcome) {
        return new AnalysisRunReference(
                run.getId(), run.getCommitSha(), run.getStatus(), run.getTriggerReason(), verdictOutcome, run.getCreatedAt());
    }
}
