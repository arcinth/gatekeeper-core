package com.gatekeeper.verdict;

import com.gatekeeper.verdict.dto.VerdictFilter;
import com.gatekeeper.verdictengine.VerdictOutcome;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/verdicts (Sprint 5
 * Milestone 3) - mirrors AIReviewRunSpecifications exactly.
 * <p>
 * No outcome-rank ordering method (contrast SecurityFindingSpecifications'
 * orderBySeverityRank / AIReviewFindingSpecifications' orderByConfidenceRank):
 * VerdictOutcome has exactly two values, and plain alphabetical sorting on
 * the column (APPROVED before BLOCKED) is already a stable, unambiguous
 * order - there is no "middle" value a naive sort could misplace the way a
 * 3-or-4-tier severity/confidence enum's could. Standard Pageable sorting on
 * {@code outcome}/{@code createdAt} is sufficient.
 */
public final class VerdictSpecifications {

    private VerdictSpecifications() {
    }

    /**
     * Fetch-joins analysisRun -> pullRequest -> repository so the list
     * view's denormalized fields cost no extra query per row. All to-one
     * associations, so fetch-join + pagination is safe.
     */
    static Specification<Verdict> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("analysisRun", JoinType.LEFT)
                        .fetch("pullRequest", JoinType.LEFT)
                        .fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<Verdict> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("analysisRun").get("id"), analysisRunId);
    }

    static Specification<Verdict> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("analysisRun").get("pullRequest").get("repository").get("id"), repositoryId);
    }

    static Specification<Verdict> hasOutcome(VerdictOutcome outcome) {
        return (root, query, builder) -> outcome == null ? null : builder.equal(root.get("outcome"), outcome);
    }

    static Specification<Verdict> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<Verdict> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Verdict> matching(VerdictFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasOutcome(filter.outcome()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()));
    }
}
