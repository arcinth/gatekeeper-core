package com.gatekeeper.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineeringReportRepository extends JpaRepository<EngineeringReport, Long> {

    /** The idempotency check ReportPublicationService.publishIfAbsent guards every publish attempt with. */
    boolean existsByAnalysisRunId(Long analysisRunId);

    /** Core read-side lookup (Milestone 1) - the REST-facing detail composition is Milestone 2's concern. */
    Optional<EngineeringReport> findByAnalysisRunId(Long analysisRunId);

    /**
     * Eagerly loads analysisRun -> pullRequest -> repository for
     * ReportQueryService's Report Detail composition (Unified Engineering
     * Report Architecture, Milestone 2 / Section 9) - mirrors
     * VerdictRepository.findWithContextById, keyed by analysisRunId instead
     * of the report's own id, since GET /api/v1/analysis-runs/{id}/report is
     * looked up by its owning AnalysisRun, never by EngineeringReport id
     * directly.
     */
    @EntityGraph(attributePaths = {"analysisRun", "analysisRun.pullRequest", "analysisRun.pullRequest.repository"})
    Optional<EngineeringReport> findWithContextByAnalysisRunId(Long analysisRunId);

    /**
     * Backs ReportTimeoutSweepJob (Section 6 / ADR-046): AnalysisRuns whose
     * Verdict landed before {@code cutoff} but that still have no matching
     * EngineeringReport row - i.e. AI review's own terminal signal never
     * arrived to trigger the normal join. Queries Verdict directly rather
     * than AnalysisRun, since a Verdict's existence (not the run's own
     * status) is what the report-generation join actually waits on.
     */
    @Query("SELECT v.analysisRun.id FROM Verdict v WHERE v.createdAt < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM EngineeringReport r WHERE r.analysisRun.id = v.analysisRun.id)")
    List<Long> findAnalysisRunIdsMissingReportPublishedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Dashboard overview aggregate (Unified Engineering Report Architecture,
     * Section 11) - computed in SQL, never in-memory, mirroring
     * VerdictRepository.countByOutcomeSince exactly. Read-only aggregation
     * only; does not touch report generation or publication logic.
     */
    @Query("SELECT r.aiReviewStatus, COUNT(r) FROM EngineeringReport r "
            + "WHERE r.publishedAt >= :since GROUP BY r.aiReviewStatus")
    List<Object[]> countByAiReviewStatusSince(@Param("since") Instant since);

    /**
     * Repository Governance View aggregate (Repository Governance View
     * Architecture, Section 6) - mirrors countByAiReviewStatusSince with an
     * added repository filter, computed in SQL, never in-memory. Read-only
     * aggregation only; does not touch report generation or publication logic.
     */
    @Query("SELECT r.aiReviewStatus, COUNT(r) FROM EngineeringReport r "
            + "WHERE r.publishedAt >= :since AND r.analysisRun.pullRequest.repository.id = :repositoryId "
            + "GROUP BY r.aiReviewStatus")
    List<Object[]> countByAiReviewStatusSinceForRepository(
            @Param("since") Instant since, @Param("repositoryId") Long repositoryId);
}
