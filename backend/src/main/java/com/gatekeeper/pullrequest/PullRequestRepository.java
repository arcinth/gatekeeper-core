package com.gatekeeper.pullrequest;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long>, JpaSpecificationExecutor<PullRequest> {

    Optional<PullRequest> findByGithubPrId(Long githubPrId);

    /** Eagerly loads repository for GET /api/v1/pull-requests/{id}, avoiding a second lazy-load query for it. */
    @EntityGraph(attributePaths = {"repository"})
    Optional<PullRequest> findWithRepositoryById(Long id);
}
