package com.gatekeeper.verdict.dto;

import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;

/**
 * List-view row for GET /api/v1/verdicts (Sprint 5 Milestone 3). Denormalizes
 * repository/PR context onto the row itself, mirroring
 * AIReviewRunSummaryResponse's flat style. reasonsTotal is populated by the
 * service layer from a separate batched count query over the page's ids, not
 * by this factory method - the same findingsTotal/AnalysisRunSummaryResponse
 * precedent, applied here to reasons instead of findings.
 */
public record VerdictSummaryResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        VerdictOutcome outcome,
        Instant createdAt,
        long reasonsTotal) {

    public static VerdictSummaryResponse from(Verdict verdict, long reasonsTotal) {
        return new VerdictSummaryResponse(
                verdict.getId(),
                verdict.getAnalysisRun().getId(),
                verdict.getAnalysisRun().getPullRequest().getRepository().getFullName(),
                verdict.getAnalysisRun().getPullRequest().getNumber(),
                verdict.getAnalysisRun().getCommitSha(),
                verdict.getOutcome(),
                verdict.getCreatedAt(),
                reasonsTotal);
    }
}
