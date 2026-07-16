package com.gatekeeper.aireviewrun;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Extended for the read/query API (Sprint 4 Milestone 4) - mirrors
 * SecurityFindingRepository's own Milestone 3 extension, applied here to
 * AIReviewRun instead of a finding entity.
 */
public interface AIReviewRunRepository extends JpaRepository<AIReviewRun, Long>,
        JpaSpecificationExecutor<AIReviewRun> {

    /** Eagerly loads analysisRun -> pullRequest -> repository for the single-run detail endpoint. */
    @EntityGraph(attributePaths = {"analysisRun", "analysisRun.pullRequest", "analysisRun.pullRequest.repository"})
    Optional<AIReviewRun> findWithContextById(Long id);

    /**
     * Single-run lookup for ReportPublicationService.onVerdictProduced
     * (Unified Engineering Report Architecture, Milestone 1) - checks
     * whether AI review already reached a terminal state for this run.
     * Purely additive: AI Review Engine's own decision logic is untouched.
     */
    Optional<AIReviewRun> findByAnalysisRunId(Long analysisRunId);

    /** Dashboard overview aggregate - computed in SQL, never in-memory (mirrors AnalysisRunRepository.countByStatusSince). */
    @Query("SELECT r.status, COUNT(r) FROM AIReviewRun r WHERE r.createdAt >= :since GROUP BY r.status")
    List<Object[]> countByStatusSince(@Param("since") Instant since);
}
