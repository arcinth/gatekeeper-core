package com.gatekeeper.analysisrun;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long>, JpaSpecificationExecutor<AnalysisRun> {

    Optional<AnalysisRun> findByPullRequestIdAndCommitSha(Long pullRequestId, String commitSha);

    /**
     * Eagerly loads the pullRequest -> repository -> githubInstallation chain
     * in the same query. Needed specifically where the returned AnalysisRun
     * will be navigated *after* the loading transaction has closed (see
     * AnalysisExecutionService) - without this, those lazy associations would
     * throw LazyInitializationException the moment they're accessed outside
     * a session, since Milestone 4's execution phase deliberately holds no
     * transaction across the GitHub API call (Section 6 / ADR-016). Reused by
     * AnalysisRunService.findDetailByIdOrThrow (Milestone 5) for the same reason:
     * the detail response is assembled from an entity whose associations must
     * already be loaded.
     */
    @EntityGraph(attributePaths = {"pullRequest", "pullRequest.repository", "pullRequest.repository.githubInstallation"})
    Optional<AnalysisRun> findWithPullRequestAndRepositoryById(Long id);

    /** Dashboard overview aggregate (Milestone 5 Architecture, Section 8) - computed in SQL, never in-memory. */
    @Query("SELECT ar.status, COUNT(ar) FROM AnalysisRun ar WHERE ar.createdAt >= :since GROUP BY ar.status")
    List<Object[]> countByStatusSince(@Param("since") Instant since);
}
