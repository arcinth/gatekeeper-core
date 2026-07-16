package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;

/**
 * List-view row for GET /api/v1/analysis-runs (Milestone 5 Architecture, Section 2).
 * findingsTotal and securityFindingsTotal are populated by the service layer
 * from separate batched count queries over the page's ids, not by this factory
 * method - see AnalysisRunService.findSummaryPage and the architecture's
 * Section 8 rationale for why those counts are deliberately not part of the
 * same filtered/paginated query.
 * <p>
 * securityFindingsTotal was added alongside the existing findingsTotal
 * (Security Engine Architecture, Section 13) rather than renaming it to
 * something like policyFindingsTotal - findingsTotal keeps its established,
 * now implicitly Policy-only meaning so no existing consumer of this field
 * breaks.
 * <p>
 * verdictOutcome was added the same additive way (Sprint 5 Architecture,
 * Section 14 / ADR-043) - nullable, since it is null for any run that has
 * not yet reached COMPLETED (a Verdict only ever exists alongside a
 * COMPLETED AnalysisRun, ADR-039).
 */
public record AnalysisRunSummaryResponse(
        Long id,
        Long repositoryId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String pullRequestTitle,
        String commitSha,
        AnalysisRunStatus status,
        AnalysisRunTriggerReason triggerReason,
        Instant createdAt,
        Instant updatedAt,
        long findingsTotal,
        long securityFindingsTotal,
        VerdictOutcome verdictOutcome) {

    public static AnalysisRunSummaryResponse from(
            AnalysisRun run, long findingsTotal, long securityFindingsTotal, VerdictOutcome verdictOutcome) {
        return new AnalysisRunSummaryResponse(
                run.getId(),
                run.getPullRequest().getRepository().getId(),
                run.getPullRequest().getRepository().getFullName(),
                run.getPullRequest().getNumber(),
                run.getPullRequest().getTitle(),
                run.getCommitSha(),
                run.getStatus(),
                run.getTriggerReason(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                findingsTotal,
                securityFindingsTotal,
                verdictOutcome);
    }
}
