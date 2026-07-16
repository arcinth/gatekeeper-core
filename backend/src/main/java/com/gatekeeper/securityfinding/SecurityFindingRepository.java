package com.gatekeeper.securityfinding;

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
 * Extended for the read/query API (Sprint 3 Milestone 3) - mirrors
 * PolicyFindingRepository's own Milestone 5 extension exactly, one milestone
 * later in this engine's own history, same as its persistence layer was one
 * milestone later than Policy's (Sprint 2 Milestone 4 -> Sprint 3 Milestone 2).
 */
public interface SecurityFindingRepository extends JpaRepository<SecurityFindingEntity, Long>,
        JpaSpecificationExecutor<SecurityFindingEntity> {

    /** Eagerly loads analysisRun -> pullRequest -> repository for the single-finding detail endpoint. */
    @EntityGraph(attributePaths = {"analysisRun", "analysisRun.pullRequest", "analysisRun.pullRequest.repository"})
    Optional<SecurityFindingEntity> findWithContextById(Long id);

    /**
     * Every finding for one run, in a stable order - backs ReportQueryService's
     * Unified Engineering Report composition (Unified Engineering Report
     * Architecture, Milestone 2). Mirrors PolicyFindingRepository's own
     * addition - see its Javadoc for why no separate EntityGraph is needed.
     */
    List<SecurityFindingEntity> findByAnalysisRunIdOrderById(Long analysisRunId);

    /** Batched per-run findings count for the analysis-run list view. */
    @Query("SELECT sf.analysisRun.id, COUNT(sf) FROM SecurityFindingEntity sf "
            + "WHERE sf.analysisRun.id IN :analysisRunIds GROUP BY sf.analysisRun.id")
    List<Object[]> countByAnalysisRunIdIn(@Param("analysisRunIds") Collection<Long> analysisRunIds);

    /** Findings-by-severity breakdown for a single run's detail view. */
    @Query("SELECT sf.severity, COUNT(sf) FROM SecurityFindingEntity sf "
            + "WHERE sf.analysisRun.id = :analysisRunId GROUP BY sf.severity")
    List<Object[]> countBySeverityForAnalysisRun(@Param("analysisRunId") Long analysisRunId);

    /** Dashboard overview aggregates - computed in SQL, never in-memory. */
    @Query("SELECT sf.severity, COUNT(sf) FROM SecurityFindingEntity sf WHERE sf.createdAt >= :since GROUP BY sf.severity")
    List<Object[]> countBySeveritySince(@Param("since") Instant since);

    @Query("SELECT sf.category, COUNT(sf) FROM SecurityFindingEntity sf WHERE sf.createdAt >= :since GROUP BY sf.category")
    List<Object[]> countByCategorySince(@Param("since") Instant since);

    /**
     * Repository Governance View aggregates (Repository Governance View
     * Architecture, Section 6) - mirror countBySeveritySince/countByCategorySince
     * with an added repository filter, computed in SQL, never in-memory.
     */
    @Query("SELECT sf.severity, COUNT(sf) FROM SecurityFindingEntity sf "
            + "WHERE sf.createdAt >= :since AND sf.analysisRun.pullRequest.repository.id = :repositoryId GROUP BY sf.severity")
    List<Object[]> countBySeveritySinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);

    @Query("SELECT sf.category, COUNT(sf) FROM SecurityFindingEntity sf "
            + "WHERE sf.createdAt >= :since AND sf.analysisRun.pullRequest.repository.id = :repositoryId GROUP BY sf.category")
    List<Object[]> countByCategorySinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);
}
