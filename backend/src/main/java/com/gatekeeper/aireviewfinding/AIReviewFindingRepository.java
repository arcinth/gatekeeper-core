package com.gatekeeper.aireviewfinding;

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
 * Extended for the read/query API (Sprint 4 Milestone 4) - mirrors
 * SecurityFindingRepository exactly, one indirection level deeper: every
 * query here joins through {@code aiReviewRun} to reach AnalysisRun, since
 * AIReviewFindingEntity belongs to AIReviewRun, not directly to AnalysisRun
 * (see AIReviewRun's own Javadoc for why).
 */
public interface AIReviewFindingRepository extends JpaRepository<AIReviewFindingEntity, Long>,
        JpaSpecificationExecutor<AIReviewFindingEntity> {

    /** Eagerly loads aiReviewRun -> analysisRun -> pullRequest -> repository for the single-finding detail endpoint. */
    @EntityGraph(attributePaths = {
            "aiReviewRun", "aiReviewRun.analysisRun",
            "aiReviewRun.analysisRun.pullRequest", "aiReviewRun.analysisRun.pullRequest.repository"})
    Optional<AIReviewFindingEntity> findWithContextById(Long id);

    /** Batched per-run findings count for the AI review runs list view. */
    @Query("SELECT f.aiReviewRun.id, COUNT(f) FROM AIReviewFindingEntity f "
            + "WHERE f.aiReviewRun.id IN :aiReviewRunIds GROUP BY f.aiReviewRun.id")
    List<Object[]> countByAiReviewRunIdIn(@Param("aiReviewRunIds") Collection<Long> aiReviewRunIds);

    /** Findings-by-confidence breakdown for a single run's detail view. */
    @Query("SELECT f.confidence, COUNT(f) FROM AIReviewFindingEntity f "
            + "WHERE f.aiReviewRun.id = :aiReviewRunId GROUP BY f.confidence")
    List<Object[]> countByConfidenceForAiReviewRun(@Param("aiReviewRunId") Long aiReviewRunId);

    /** Dashboard overview aggregates - computed in SQL, never in-memory. */
    @Query("SELECT f.confidence, COUNT(f) FROM AIReviewFindingEntity f WHERE f.createdAt >= :since GROUP BY f.confidence")
    List<Object[]> countByConfidenceSince(@Param("since") Instant since);

    @Query("SELECT f.type, COUNT(f) FROM AIReviewFindingEntity f WHERE f.createdAt >= :since GROUP BY f.type")
    List<Object[]> countByTypeSince(@Param("since") Instant since);
}
