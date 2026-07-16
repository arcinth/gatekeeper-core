package com.gatekeeper.verdict.dto;

import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.util.List;

/**
 * Single-verdict detail response for GET /api/v1/verdicts/{id} (Sprint 5
 * Milestone 3) - carries every VerdictReason, unlike the summary row, which
 * only carries a count.
 */
public record VerdictDetailResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        VerdictOutcome outcome,
        Instant createdAt,
        List<VerdictReasonSummary> reasons) {

    public static VerdictDetailResponse from(Verdict verdict, List<VerdictReasonSummary> reasons) {
        return new VerdictDetailResponse(
                verdict.getId(),
                verdict.getAnalysisRun().getId(),
                verdict.getAnalysisRun().getPullRequest().getRepository().getFullName(),
                verdict.getAnalysisRun().getPullRequest().getNumber(),
                verdict.getAnalysisRun().getCommitSha(),
                verdict.getOutcome(),
                verdict.getCreatedAt(),
                reasons);
    }
}
