package com.gatekeeper.pullrequest.dto;

import com.gatekeeper.pullrequest.PullRequestStatus;

/** All fields optional - null means "not filtered on this criterion" (mirrors AnalysisRunFilter's convention). */
public record PullRequestFilter(Long repositoryId, PullRequestStatus status) {
}
