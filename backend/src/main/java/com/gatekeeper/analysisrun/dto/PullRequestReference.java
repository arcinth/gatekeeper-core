package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestStatus;

/** Minimal pull request context embedded in an AnalysisRun detail response. */
public record PullRequestReference(
        Integer number,
        String title,
        String authorLogin,
        String sourceBranch,
        String targetBranch,
        String headSha,
        PullRequestStatus status) {

    public static PullRequestReference from(PullRequest pullRequest) {
        return new PullRequestReference(
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                pullRequest.getAuthorLogin(),
                pullRequest.getSourceBranch(),
                pullRequest.getTargetBranch(),
                pullRequest.getHeadSha(),
                pullRequest.getStatus());
    }
}
