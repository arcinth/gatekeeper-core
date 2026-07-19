package com.gatekeeper.pullrequest.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;

/**
 * List-view row for GET /api/v1/pull-requests - the reviewer's primary
 * workspace, not just another list page, so this denormalizes repository and
 * GitHub-facing fields directly onto the row (flat style, mirroring
 * AnalysisRunSummaryResponse/AIReviewRunSummaryResponse's own list-view
 * convention) rather than nesting a reference object the way the detail
 * response does.
 * <p>
 * latestAnalysisRunId/Status and latestVerdictOutcome are populated by
 * PullRequestService from two batched supplementary queries over the page's
 * ids - the same shape AnalysisRunService.findSummaryPage already
 * established for its own per-row enrichment - not part of this factory
 * method or the page's own filtered/paginated query. All three are null for
 * a Pull Request with no analysis run yet.
 */
public record PullRequestSummaryResponse(
        Long id,
        Integer number,
        String title,
        Long repositoryId,
        String repositoryFullName,
        String repositoryName,
        String repositoryOwner,
        String authorLogin,
        String sourceBranch,
        String targetBranch,
        PullRequestStatus status,
        String githubUrl,
        Long latestAnalysisRunId,
        AnalysisRunStatus latestAnalysisRunStatus,
        VerdictOutcome latestVerdictOutcome,
        Instant createdAt,
        Instant updatedAt) {

    public static PullRequestSummaryResponse from(
            PullRequest pullRequest, AnalysisRun latestAnalysisRun, VerdictOutcome latestVerdictOutcome) {
        Repository repository = pullRequest.getRepository();
        return new PullRequestSummaryResponse(
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                repository.getId(),
                repository.getFullName(),
                repository.getName(),
                repository.getOwner(),
                pullRequest.getAuthorLogin(),
                pullRequest.getSourceBranch(),
                pullRequest.getTargetBranch(),
                pullRequest.getStatus(),
                githubUrl(repository.getFullName(), pullRequest.getNumber()),
                latestAnalysisRun == null ? null : latestAnalysisRun.getId(),
                latestAnalysisRun == null ? null : latestAnalysisRun.getStatus(),
                latestVerdictOutcome,
                pullRequest.getCreatedAt(),
                pullRequest.getUpdatedAt());
    }

    /** Shared with PullRequestDetailResponse - GitHub has no API for this, but the URL shape is fixed and documented. */
    static String githubUrl(String repositoryFullName, Integer number) {
        return "https://github.com/" + repositoryFullName + "/pull/" + number;
    }
}
