package com.gatekeeper.reviewdecision;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    /** Full decision history for one AnalysisRun, newest first. reviewer is fetched eagerly to avoid N+1 when mapping to responses. */
    @EntityGraph(attributePaths = {"reviewer"})
    List<ReviewDecision> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);
}
