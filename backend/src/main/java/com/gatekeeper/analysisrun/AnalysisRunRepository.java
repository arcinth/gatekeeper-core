package com.gatekeeper.analysisrun;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    Optional<AnalysisRun> findByPullRequestIdAndCommitSha(Long pullRequestId, String commitSha);

    /**
     * Eagerly loads the pullRequest -> repository -> githubInstallation chain
     * in the same query. Needed specifically where the returned AnalysisRun
     * will be navigated *after* the loading transaction has closed (see
     * AnalysisExecutionService) - without this, those lazy associations would
     * throw LazyInitializationException the moment they're accessed outside
     * a session, since Milestone 4's execution phase deliberately holds no
     * transaction across the GitHub API call (Section 6 / ADR-016).
     */
    @EntityGraph(attributePaths = {"pullRequest", "pullRequest.repository", "pullRequest.repository.githubInstallation"})
    Optional<AnalysisRun> findWithPullRequestAndRepositoryById(Long id);
}
