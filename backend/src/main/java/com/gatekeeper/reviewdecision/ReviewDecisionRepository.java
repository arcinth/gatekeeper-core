package com.gatekeeper.reviewdecision;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    /** Full decision history for one AnalysisRun, newest first. reviewer is fetched eagerly to avoid N+1 when mapping to responses. */
    @EntityGraph(attributePaths = {"reviewer"})
    List<ReviewDecision> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);

    /**
     * The single most recent decision for one AnalysisRun - what
     * GitHubReviewDecisionCheckRunService publishes (the check run reflects
     * the current/latest decision, not every individual one, the same
     * "latest wins" precedent PullRequestService's own list-view enrichment
     * already established for "latest analysis run per PR").
     */
    @EntityGraph(attributePaths = {"reviewer"})
    Optional<ReviewDecision> findFirstByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);
}
