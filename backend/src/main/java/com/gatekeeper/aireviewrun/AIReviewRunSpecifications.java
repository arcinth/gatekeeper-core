package com.gatekeeper.aireviewrun;

import com.gatekeeper.aireviewrun.dto.AIReviewRunFilter;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/ai-review-runs (Sprint 4
 * Milestone 4) - mirrors AnalysisRunSpecifications/SecurityFindingSpecifications
 * exactly: dynamic Specification over derived query methods, fetch-join
 * guarded against the separate COUNT query.
 */
public final class AIReviewRunSpecifications {

    private AIReviewRunSpecifications() {
    }

    /**
     * Fetch-joins analysisRun -> pullRequest -> repository so the list view's
     * denormalized fields cost no extra query per row. All to-one
     * associations, so fetch-join + pagination is safe.
     */
    static Specification<AIReviewRun> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("analysisRun", JoinType.LEFT)
                        .fetch("pullRequest", JoinType.LEFT)
                        .fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<AIReviewRun> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("analysisRun").get("id"), analysisRunId);
    }

    static Specification<AIReviewRun> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("analysisRun").get("pullRequest").get("repository").get("id"), repositoryId);
    }

    static Specification<AIReviewRun> hasStatus(AIReviewRunStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    static Specification<AIReviewRun> hasProvider(String provider) {
        return (root, query, builder) -> provider == null || provider.isBlank() ? null
                : builder.equal(root.get("provider"), provider);
    }

    static Specification<AIReviewRun> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AIReviewRun> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<AIReviewRun> matching(AIReviewRunFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasStatus(filter.status()))
                .and(hasProvider(filter.provider()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()));
    }
}
