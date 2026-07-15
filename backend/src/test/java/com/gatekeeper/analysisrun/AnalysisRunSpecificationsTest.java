package com.gatekeeper.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.dto.AnalysisRunFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pure predicate-logic tests, no Spring context (Milestone 5 Architecture,
 * Section 13) - verifies each filter contributes the right predicate (or none,
 * when its value is null) without needing a real database.
 */
class AnalysisRunSpecificationsTest {

    private final Root<AnalysisRun> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        Predicate predicate = AnalysisRunSpecifications.hasRepositoryId(null).toPredicate(root, query, builder);

        assertThat(predicate).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasRepositoryId_buildsAnEqualityPredicateOnPullRequestRepositoryId() {
        Path<Object> pullRequestPath = mock(Path.class);
        Path<Object> repositoryPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("pullRequest")).thenReturn(pullRequestPath);
        when(pullRequestPath.get("repository")).thenReturn(repositoryPath);
        when(repositoryPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 7L)).thenReturn(expected);

        Predicate predicate = AnalysisRunSpecifications.hasRepositoryId(7L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasStatus_returnsNullPredicateWhenNull() {
        assertThat(AnalysisRunSpecifications.hasStatus(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasStatus_buildsAnEqualityPredicate() {
        Path<Object> statusPath = mock(Path.class);
        when(root.get("status")).thenReturn(statusPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(statusPath, AnalysisRunStatus.COMPLETED)).thenReturn(expected);

        Predicate predicate = AnalysisRunSpecifications.hasStatus(AnalysisRunStatus.COMPLETED)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasTriggerReason_returnsNullPredicateWhenNull() {
        assertThat(AnalysisRunSpecifications.hasTriggerReason(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasTriggerReason_buildsAnEqualityPredicate() {
        Path<Object> triggerReasonPath = mock(Path.class);
        when(root.get("triggerReason")).thenReturn(triggerReasonPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(triggerReasonPath, AnalysisRunTriggerReason.OPENED)).thenReturn(expected);

        Predicate predicate = AnalysisRunSpecifications.hasTriggerReason(AnalysisRunTriggerReason.OPENED)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(AnalysisRunSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdFrom_buildsAGreaterThanOrEqualPredicate() {
        Path<Instant> createdAtPath = mock(Path.class);
        when(root.<Instant>get("createdAt")).thenReturn(createdAtPath);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Predicate expected = mock(Predicate.class);
        when(builder.greaterThanOrEqualTo(createdAtPath, from)).thenReturn(expected);

        Predicate predicate = AnalysisRunSpecifications.createdFrom(from).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(AnalysisRunSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_buildsALessThanOrEqualPredicate() {
        Path<Instant> createdAtPath = mock(Path.class);
        when(root.<Instant>get("createdAt")).thenReturn(createdAtPath);
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        Predicate expected = mock(Predicate.class);
        when(builder.lessThanOrEqualTo(createdAtPath, to)).thenReturn(expected);

        Predicate predicate = AnalysisRunSpecifications.createdTo(to).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void withFetchedAssociations_fetchesPullRequestAndRepositoryForAnEntityResultQuery() {
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) AnalysisRun.class);
        when(root.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AnalysisRunSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AnalysisRunSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) AnalysisRun.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        AnalysisRunFilter filter = new AnalysisRunFilter(1L, AnalysisRunStatus.COMPLETED,
                AnalysisRunTriggerReason.OPENED, Instant.EPOCH, Instant.now());

        org.springframework.data.jpa.domain.Specification<AnalysisRun> spec = AnalysisRunSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }
}
