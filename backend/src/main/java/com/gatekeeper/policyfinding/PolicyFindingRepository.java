package com.gatekeeper.policyfinding;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyFindingRepository extends JpaRepository<PolicyFindingEntity, Long>,
        JpaSpecificationExecutor<PolicyFindingEntity> {

    /** Eagerly loads analysisRun -> pullRequest -> repository for the single-finding detail endpoint. */
    @EntityGraph(attributePaths = {"analysisRun", "analysisRun.pullRequest", "analysisRun.pullRequest.repository"})
    Optional<PolicyFindingEntity> findWithContextById(Long id);

    /**
     * Every finding for one run, in a stable order - backs ReportQueryService's
     * Unified Engineering Report composition (Unified Engineering Report
     * Architecture, Milestone 2). No separate EntityGraph needed here: the
     * caller always loads the owning AnalysisRun first via
     * EngineeringReportRepository.findWithContextByAnalysisRunId, so each
     * row's lazy analysisRun/pullRequest/repository navigation resolves
     * against Hibernate's session cache rather than issuing new queries.
     */
    List<PolicyFindingEntity> findByAnalysisRunIdOrderById(Long analysisRunId);

    /**
     * Batched per-run findings count for the analysis-run list view (Milestone
     * 5 Architecture, Section 8 / ADR-021) - queried separately from the
     * filtered/paginated run query itself, not combined with it.
     */
    @Query("SELECT pf.analysisRun.id, COUNT(pf) FROM PolicyFindingEntity pf "
            + "WHERE pf.analysisRun.id IN :analysisRunIds GROUP BY pf.analysisRun.id")
    List<Object[]> countByAnalysisRunIdIn(@Param("analysisRunIds") Collection<Long> analysisRunIds);

    /** Findings-by-severity breakdown for a single run's detail view. */
    @Query("SELECT pf.severity, COUNT(pf) FROM PolicyFindingEntity pf "
            + "WHERE pf.analysisRun.id = :analysisRunId GROUP BY pf.severity")
    List<Object[]> countBySeverityForAnalysisRun(@Param("analysisRunId") Long analysisRunId);

    /** Dashboard overview aggregates (Milestone 5 Architecture, Section 8) - computed in SQL, never in-memory. */
    @Query("SELECT pf.severity, COUNT(pf) FROM PolicyFindingEntity pf WHERE pf.createdAt >= :since GROUP BY pf.severity")
    List<Object[]> countBySeveritySince(@Param("since") Instant since);

    @Query("SELECT pf.category, COUNT(pf) FROM PolicyFindingEntity pf WHERE pf.createdAt >= :since GROUP BY pf.category")
    List<Object[]> countByCategorySince(@Param("since") Instant since);

    /**
     * Repository Governance View aggregates (Repository Governance View
     * Architecture, Section 6) - mirror countBySeveritySince/countByCategorySince
     * with an added repository filter, computed in SQL, never in-memory.
     */
    @Query("SELECT pf.severity, COUNT(pf) FROM PolicyFindingEntity pf "
            + "WHERE pf.createdAt >= :since AND pf.analysisRun.pullRequest.repository.id = :repositoryId GROUP BY pf.severity")
    List<Object[]> countBySeveritySinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);

    @Query("SELECT pf.category, COUNT(pf) FROM PolicyFindingEntity pf "
            + "WHERE pf.createdAt >= :since AND pf.analysisRun.pullRequest.repository.id = :repositoryId GROUP BY pf.category")
    List<Object[]> countByCategorySinceForRepository(@Param("since") Instant since, @Param("repositoryId") Long repositoryId);
}
