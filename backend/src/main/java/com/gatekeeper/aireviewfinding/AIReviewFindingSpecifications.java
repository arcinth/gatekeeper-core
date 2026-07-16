package com.gatekeeper.aireviewfinding;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/ai-review-findings
 * (Sprint 4 Milestone 4) - mirrors SecurityFindingSpecifications exactly,
 * one indirection level deeper (aiReviewRun.analysisRun... instead of
 * analysisRun... directly), same rationale for every technique used.
 */
public final class AIReviewFindingSpecifications {

    private AIReviewFindingSpecifications() {
    }

    /**
     * Fetch-joins aiReviewRun -> analysisRun -> pullRequest -> repository so
     * the flat, cross-run listing's denormalized fields cost no extra query
     * per row. All to-one associations, so fetch-join + pagination is safe.
     * Guarded on result type because Spring Data JPA reuses this
     * Specification to build the separate COUNT query for pagination totals.
     */
    static Specification<AIReviewFindingEntity> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("aiReviewRun", JoinType.LEFT)
                        .fetch("analysisRun", JoinType.LEFT)
                        .fetch("pullRequest", JoinType.LEFT)
                        .fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<AIReviewFindingEntity> hasAiReviewRunId(Long aiReviewRunId) {
        return (root, query, builder) -> aiReviewRunId == null ? null
                : builder.equal(root.get("aiReviewRun").get("id"), aiReviewRunId);
    }

    static Specification<AIReviewFindingEntity> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("aiReviewRun").get("analysisRun").get("id"), analysisRunId);
    }

    static Specification<AIReviewFindingEntity> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("aiReviewRun").get("analysisRun").get("pullRequest").get("repository").get("id"),
                        repositoryId);
    }

    static Specification<AIReviewFindingEntity> hasConfidence(AIReviewConfidence confidence) {
        return (root, query, builder) -> confidence == null ? null : builder.equal(root.get("confidence"), confidence);
    }

    static Specification<AIReviewFindingEntity> hasType(AIReviewFindingType type) {
        return (root, query, builder) -> type == null ? null : builder.equal(root.get("type"), type);
    }

    static Specification<AIReviewFindingEntity> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AIReviewFindingEntity> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<AIReviewFindingEntity> matching(AIReviewFindingFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasAiReviewRunId(filter.aiReviewRunId()))
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasConfidence(filter.confidence()))
                .and(hasType(filter.type()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()));
    }

    /**
     * AIReviewConfidence is stored as its enum name (LOW/MEDIUM/HIGH), so a
     * naive "ORDER BY confidence" sorts alphabetically (HIGH, LOW, MEDIUM) -
     * wrong. Same technique as SecurityFindingSpecifications.orderBySeverityRank,
     * a 3-tier CASE instead of 4-tier since AIReviewConfidence has one fewer
     * value than SecuritySeverity. The caller (AIReviewFindingQueryService)
     * must pass an unsorted Pageable whenever this is used, since Spring Data
     * only overwrites a Specification's manual orderBy when the Pageable's
     * Sort is non-empty.
     */
    public static Specification<AIReviewFindingEntity> orderByConfidenceRank(Sort.Direction direction) {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                Expression<Integer> rank = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("confidence"), AIReviewConfidence.HIGH), 0)
                        .when(builder.equal(root.get("confidence"), AIReviewConfidence.MEDIUM), 1)
                        .otherwise(2);
                // Rank 0 = HIGH: DESC confidence (highest first) means ascending rank.
                query.orderBy(direction.isDescending() ? builder.asc(rank) : builder.desc(rank));
            }
            return builder.conjunction();
        };
    }
}
