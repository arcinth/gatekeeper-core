package com.gatekeeper.pullrequest.dto;

import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import java.time.Instant;
import java.util.List;

/**
 * Detail view for GET /api/v1/pull-requests/{id}. Nests RepositoryContext
 * (detail-view "reference" style, mirroring AnalysisRunDetailResponse) and
 * carries the Pull Request's complete analysis-run history, newest first, so
 * a reviewer sees every commit this PR has been analyzed at without a second
 * round-trip to GET /api/v1/analysis-runs?... - the same "assemble the full
 * picture in one response" reasoning AnalysisRunDetailResponse's own
 * embedded verdictReasons already established.
 * <p>
 * The Review section itself (Milestone 2) is deliberately not part of this
 * response yet - this milestone is read/navigation only.
 */
public record PullRequestDetailResponse(
        Long id,
        Integer number,
        String title,
        RepositoryContext repository,
        String authorLogin,
        String sourceBranch,
        String targetBranch,
        String headSha,
        PullRequestStatus status,
        String githubUrl,
        Instant createdAt,
        Instant updatedAt,
        List<AnalysisRunReference> analysisRuns) {

    public static PullRequestDetailResponse from(PullRequest pullRequest, List<AnalysisRunReference> analysisRuns) {
        Repository repository = pullRequest.getRepository();
        return new PullRequestDetailResponse(
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                RepositoryContext.from(repository),
                pullRequest.getAuthorLogin(),
                pullRequest.getSourceBranch(),
                pullRequest.getTargetBranch(),
                pullRequest.getHeadSha(),
                pullRequest.getStatus(),
                PullRequestSummaryResponse.githubUrl(repository.getFullName(), pullRequest.getNumber()),
                pullRequest.getCreatedAt(),
                pullRequest.getUpdatedAt(),
                analysisRuns);
    }
}
