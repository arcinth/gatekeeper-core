package com.gatekeeper.analysisrun;

import com.gatekeeper.analysisrun.dto.AnalysisRunFilter;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/analysis-runs (Milestone 5
 * Architecture, Section 5/8). Chosen over derived query methods specifically
 * because filters are optional and independently combinable - a derived method
 * per combination would be a combinatorial explosion.
 */
public final class AnalysisRunSpecifications {

    private AnalysisRunSpecifications() {
    }

    /**
     * Fetch-joins pullRequest and pullRequest.repository so the list view's
     * denormalized fields (repositoryFullName, pullRequestNumber, ...) cost no
     * extra query per row. Both are @ManyToOne (to-one) associations, so
     * fetch-join + pagination is safe here - Hibernate's well-known "cannot
     * paginate in memory" warning only applies to fetch-joined collections
     * (@OneToMany), which this isn't.
     *
     * <p>Guarded on result type because Spring Data JPA reuses this same
     * Specification to build the separate COUNT query for pagination totals;
     * without the guard, the fetch would be applied to a query whose selection
     * is a scalar Long, not the entity, which the JPA provider need not accept.
     */
    static Specification<AnalysisRun> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("pullRequest", JoinType.LEFT).fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<AnalysisRun> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("pullRequest").get("repository").get("id"), repositoryId);
    }

    static Specification<AnalysisRun> hasStatus(AnalysisRunStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    static Specification<AnalysisRun> hasTriggerReason(AnalysisRunTriggerReason triggerReason) {
        return (root, query, builder) -> triggerReason == null ? null
                : builder.equal(root.get("triggerReason"), triggerReason);
    }

    static Specification<AnalysisRun> createdFrom(Instant from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AnalysisRun> createdTo(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<AnalysisRun> matching(AnalysisRunFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasStatus(filter.status()))
                .and(hasTriggerReason(filter.triggerReason()))
                .and(createdFrom(filter.from()))
                .and(createdTo(filter.to()));
    }
}
