package com.gatekeeper.pullrequest.dto;

import com.gatekeeper.repository.Repository;

/**
 * Repository context nested in PullRequestDetailResponse. Deliberately its
 * own type rather than reusing analysisrun.dto.RepositoryReference - this
 * codebase's established convention favors a small duplicated reference type
 * per consuming domain over widening a shared one (see
 * AIReviewRunSummaryResponse's own Javadoc), and this one needs owner/name
 * fields analysisrun.dto.RepositoryReference doesn't carry.
 */
public record RepositoryContext(Long id, String name, String owner, String fullName) {

    public static RepositoryContext from(Repository repository) {
        return new RepositoryContext(
                repository.getId(), repository.getName(), repository.getOwner(), repository.getFullName());
    }
}
