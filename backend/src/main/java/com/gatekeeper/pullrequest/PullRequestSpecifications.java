package com.gatekeeper.pullrequest;

import com.gatekeeper.pullrequest.dto.PullRequestFilter;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic WHERE-clause composition for GET /api/v1/pull-requests, mirroring
 * AnalysisRunSpecifications' shape: filters are optional and independently
 * combinable, so a derived query method per combination would be a
 * combinatorial explosion.
 */
public final class PullRequestSpecifications {

    private PullRequestSpecifications() {
    }

    /**
     * Fetch-joins repository so the list view's denormalized fields
     * (repositoryFullName, repositoryOwner, ...) cost no extra query per row.
     * repository is a @ManyToOne (to-one) association, so fetch-join +
     * pagination is safe - Hibernate's "cannot paginate in memory" warning
     * only applies to fetch-joined collections, which this isn't.
     *
     * <p>Guarded on result type because Spring Data JPA reuses this same
     * Specification to build the separate COUNT query for pagination totals;
     * without the guard, the fetch would be applied to a query whose
     * selection is a scalar Long, not the entity.
     */
    static Specification<PullRequest> withFetchedAssociations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("repository", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    static Specification<PullRequest> hasRepositoryId(Long repositoryId) {
        return (root, query, builder) -> repositoryId == null ? null
                : builder.equal(root.get("repository").get("id"), repositoryId);
    }

    static Specification<PullRequest> hasStatus(PullRequestStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    public static Specification<PullRequest> matching(PullRequestFilter filter) {
        return Specification.where(withFetchedAssociations())
                .and(hasRepositoryId(filter.repositoryId()))
                .and(hasStatus(filter.status()));
    }
}
