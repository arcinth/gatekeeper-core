package com.gatekeeper.pullrequest;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Optional<PullRequest> findByGithubPrId(Long githubPrId);
}
