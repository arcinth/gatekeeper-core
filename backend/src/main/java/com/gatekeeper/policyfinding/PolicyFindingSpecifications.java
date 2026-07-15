package com.gatekeeper.policyfinding;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.dto.PolicyFindingFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/policy-findings (Milestone
 * 5 Architecture, Section 5/8) - same rationale as AnalysisRunSpecifications.
 */
public final class PolicyFindingSpecifications {

    private PolicyFindingSpecifications() {
    }

    /**
     * Fetch-joins analysisRun -> pullRequest -> repository so the flat,
     * cross-run listing's denormalized fields cost no extra query per row.
     * All to-one associations - see AnalysisRunSpecifications.withFetchedAssociations
     * for why that makes fetch-join + pagination safe, and why the result-type
     * guard is needed for the separate COUNT query Spring Data builds from this
     * same Specification.
     */
    static Specification<PolicyFindingEntity> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("analysisRun", JoinType.LEFT)
                        .fetch("pullRequest", JoinType.LEFT)
                        .fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<PolicyFindingEntity> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("analysisRun").get("id"), analysisRunId);
    }

    static Specification<PolicyFindingEntity> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("analysisRun").get("pullRequest").get("repository").get("id"), repositoryId);
    }

    static Specification<PolicyFindingEntity> hasSeverity(PolicySeverity severity) {
        return (root, query, builder) -> severity == null ? null : builder.equal(root.get("severity"), severity);
    }

    static Specification<PolicyFindingEntity> hasCategory(PolicyCategory category) {
        return (root, query, builder) -> category == null ? null : builder.equal(root.get("category"), category);
    }

    static Specification<PolicyFindingEntity> hasRuleId(String ruleId) {
        return (root, query, builder) -> ruleId == null || ruleId.isBlank() ? null
                : builder.equal(root.get("ruleId"), ruleId);
    }

    static Specification<PolicyFindingEntity> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<PolicyFindingEntity> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<PolicyFindingEntity> matching(PolicyFindingFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasSeverity(filter.severity()))
                .and(hasCategory(filter.category()))
                .and(hasRuleId(filter.ruleId()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()));
    }

    /**
     * PolicySeverity is stored as its enum name (LOW/MEDIUM/HIGH/CRITICAL), so a
     * naive "ORDER BY severity" sorts alphabetically - wrong (Milestone 5
     * Architecture, Section 5). This builds an explicit rank expression instead
     * and sets it as the query's ORDER BY directly. Spring Data JPA only calls
     * query.orderBy(...) itself when the Pageable it was given has a non-empty
     * Sort (see SimpleJpaRepository.getQuery); PolicyFindingQueryService passes
     * an unsorted Pageable whenever this Specification is used, specifically so
     * that guard leaves this ORDER BY untouched instead of overwriting it.
     */
    public static Specification<PolicyFindingEntity> orderBySeverityRank(Sort.Direction direction) {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                Expression<Integer> rank = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("severity"), PolicySeverity.CRITICAL), 0)
                        .when(builder.equal(root.get("severity"), PolicySeverity.HIGH), 1)
                        .when(builder.equal(root.get("severity"), PolicySeverity.MEDIUM), 2)
                        .otherwise(3);
                // Rank 0 = CRITICAL: DESC severity (highest first) means ascending rank.
                query.orderBy(direction.isDescending() ? builder.asc(rank) : builder.desc(rank));
            }
            return builder.conjunction();
        };
    }
}
