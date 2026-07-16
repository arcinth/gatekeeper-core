package com.gatekeeper.verdict;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Extended for the read/query API (Sprint 5 Milestone 3) - mirrors
 * AIReviewRunRepository's own Milestone-4 extension.
 */
public interface VerdictRepository extends JpaRepository<Verdict, Long>, JpaSpecificationExecutor<Verdict> {

    /** Eagerly loads analysisRun -> pullRequest -> repository for the single-verdict detail endpoint. */
    @EntityGraph(attributePaths = {"analysisRun", "analysisRun.pullRequest", "analysisRun.pullRequest.repository"})
    Optional<Verdict> findWithContextById(Long id);

    /**
     * Single-run lookup for AnalysisRunService.findDetailByIdOrThrow's
     * enrichment - returns empty for any AnalysisRun that isn't yet
     * COMPLETED, since a Verdict exists only once its AnalysisRun does
     * (Sprint 5 Architecture, ADR-039).
     */
    Optional<Verdict> findByAnalysisRunId(Long analysisRunId);

    /** Batched per-run lookup for AnalysisRunService.findSummaryPage's enrichment - one query, not N. */
    List<Verdict> findByAnalysisRunIdIn(Collection<Long> analysisRunIds);

    /** Dashboard overview aggregate - computed in SQL, never in-memory. */
    @Query("SELECT v.outcome, COUNT(v) FROM Verdict v WHERE v.createdAt >= :since GROUP BY v.outcome")
    List<Object[]> countByOutcomeSince(@Param("since") Instant since);

    /**
     * Repository Governance View aggregate (Repository Governance View
     * Architecture, Section 6) - mirrors countByOutcomeSince with an added
     * repository filter, computed in SQL, never in-memory.
     */
    @Query("SELECT v.outcome, COUNT(v) FROM Verdict v "
            + "WHERE v.createdAt >= :since AND v.analysisRun.pullRequest.repository.id = :repositoryId GROUP BY v.outcome")
    List<Object[]> countByOutcomeSinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);
}
