package com.gatekeeper.analysisrun;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long>, JpaSpecificationExecutor<AnalysisRun> {

    Optional<AnalysisRun> findByPullRequestIdAndCommitSha(Long pullRequestId, String commitSha);

    /** Full run history for one Pull Request's detail view, newest first. */
    List<AnalysisRun> findByPullRequestIdOrderByCreatedAtDesc(Long pullRequestId);

    /**
     * Backs PullRequestService's "latest run per PR" list-view enrichment: one
     * query returning every run for the given PR ids, ordered by PR id then by
     * createdAt descending within each PR - the caller keeps only the first
     * occurrence per PR id to get the most recent run, the same batched-query-
     * plus-in-memory-reduction shape findSummaryPage's own enrichment already
     * uses, rather than a native "latest per group" query.
     */
    List<AnalysisRun> findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(Collection<Long> pullRequestIds);

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

    /**
     * Repository Governance View aggregate (Repository Governance View
     * Architecture, Section 6) - the same shape as countByStatusSince, with
     * an added repository filter along the same pullRequest.repository.id
     * path AnalysisRunSpecifications.hasRepositoryId already traverses for
     * the paginated list endpoint. Computed in SQL, never in-memory.
     */
    @Query("SELECT ar.status, COUNT(ar) FROM AnalysisRun ar "
            + "WHERE ar.createdAt >= :since AND ar.pullRequest.repository.id = :repositoryId GROUP BY ar.status")
    List<Object[]> countByStatusSinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);
}
