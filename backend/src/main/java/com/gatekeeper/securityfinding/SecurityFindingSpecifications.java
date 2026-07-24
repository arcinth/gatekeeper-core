package com.gatekeeper.securityfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.dto.SecurityFindingFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/security-findings
 * (Security Engine Architecture, Section 13) - mirrors
 * com.gatekeeper.policyfinding.PolicyFindingSpecifications exactly, same
 * rationale for every technique used here (dynamic Specification over derived
 * query methods, fetch-join guarded against the separate COUNT query, manual
 * severity-rank ORDER BY).
 */
public final class SecurityFindingSpecifications {

    private SecurityFindingSpecifications() {
    }

    /**
     * Fetch-joins analysisRun -> pullRequest -> repository so the flat,
     * cross-run listing's denormalized fields cost no extra query per row.
     * All to-one associations, so fetch-join + pagination is safe. Guarded on
     * result type because Spring Data JPA reuses this Specification to build
     * the separate COUNT query for pagination totals.
     */
    static Specification<SecurityFindingEntity> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("analysisRun", JoinType.LEFT)
                        .fetch("pullRequest", JoinType.LEFT)
                        .fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<SecurityFindingEntity> hasAnalysisRunId(Long analysisRunId) {
        return (root, query, builder) -> analysisRunId == null ? null
                : builder.equal(root.get("analysisRun").get("id"), analysisRunId);
    }

    static Specification<SecurityFindingEntity> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("analysisRun").get("pullRequest").get("repository").get("id"), repositoryId);
    }

    static Specification<SecurityFindingEntity> hasSeverity(SecuritySeverity severity) {
        return (root, query, builder) -> severity == null ? null : builder.equal(root.get("severity"), severity);
    }

    static Specification<SecurityFindingEntity> hasCategory(SecurityCategory category) {
        return (root, query, builder) -> category == null ? null : builder.equal(root.get("category"), category);
    }

    static Specification<SecurityFindingEntity> hasRuleId(String ruleId) {
        return (root, query, builder) -> ruleId == null || ruleId.isBlank() ? null
                : builder.equal(root.get("ruleId"), ruleId);
    }

    static Specification<SecurityFindingEntity> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<SecurityFindingEntity> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    /**
     * Restricts the triage queue to findings that are still actionable "right
     * now": the finding's analysis run must be the most recent run recorded
     * for its pull request (a later commit that fixed the finding produces a
     * new run and leaves the old finding row in place, otherwise), and the
     * pull request itself must still be OPEN (a merged or closed pull request
     * has nothing left to triage). Without this, a finding from a superseded
     * commit or a since-merged pull request stays visible on the triage queue
     * indefinitely, even though the pull request's current verdict may no
     * longer reflect it - the exact "governance engine contradicting itself"
     * gap this filter closes.
     */
    static Specification<SecurityFindingEntity> currentOnly(boolean currentOnly) {
        return (root, query, builder) -> {
            if (!currentOnly) {
                return null;
            }
            Subquery<Long> latestRunIdForPullRequest = query.subquery(Long.class);
            Root<AnalysisRun> latestRunRoot = latestRunIdForPullRequest.from(AnalysisRun.class);
            latestRunIdForPullRequest
                    .select(builder.max(latestRunRoot.get("id")))
                    .where(builder.equal(
                            latestRunRoot.get("pullRequest").get("id"),
                            root.get("analysisRun").get("pullRequest").get("id")));
            return builder.and(
                    builder.equal(root.get("analysisRun").get("id"), latestRunIdForPullRequest),
                    builder.equal(root.get("analysisRun").get("pullRequest").get("status"), PullRequestStatus.OPEN));
        };
    }

    public static Specification<SecurityFindingEntity> matching(SecurityFindingFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasAnalysisRunId(filter.analysisRunId()))
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasSeverity(filter.severity()))
                .and(hasCategory(filter.category()))
                .and(hasRuleId(filter.ruleId()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()))
                .and(currentOnly(filter.currentOnly()));
    }

    /**
     * SecuritySeverity is stored as its enum name (LOW/MEDIUM/HIGH/CRITICAL),
     * so a naive "ORDER BY severity" sorts alphabetically - wrong, same
     * reasoning as PolicyFindingSpecifications.orderBySeverityRank. Sets the
     * query's ORDER BY directly via an explicit rank CASE expression; the
     * caller (SecurityFindingQueryService) must pass an unsorted Pageable
     * whenever this is used, since Spring Data only overwrites a
     * Specification's manual orderBy when the Pageable's Sort is non-empty.
     */
    public static Specification<SecurityFindingEntity> orderBySeverityRank(Sort.Direction direction) {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                Expression<Integer> rank = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("severity"), SecuritySeverity.CRITICAL), 0)
                        .when(builder.equal(root.get("severity"), SecuritySeverity.HIGH), 1)
                        .when(builder.equal(root.get("severity"), SecuritySeverity.MEDIUM), 2)
                        .otherwise(3);
                // Rank 0 = CRITICAL: DESC severity (highest first) means ascending rank.
                query.orderBy(direction.isDescending() ? builder.asc(rank) : builder.desc(rank));
            }
            return builder.conjunction();
        };
    }
}
