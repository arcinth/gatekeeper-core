package com.gatekeeper.aireviewrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewrun.dto.AIReviewRunFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AIReviewRunSpecificationsTest {

    private final Root<AIReviewRun> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasAnalysisRunId_returnsNullPredicateWhenNull() {
        Predicate predicate = AIReviewRunSpecifications.hasAnalysisRunId(null).toPredicate(root, query, builder);

        assertThat(predicate).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasAnalysisRunId_buildsAnEqualityPredicate() {
        Path<Object> analysisRunPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("analysisRun")).thenReturn(analysisRunPath);
        when(analysisRunPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 5L)).thenReturn(expected);

        Predicate predicate = AIReviewRunSpecifications.hasAnalysisRunId(5L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        assertThat(AIReviewRunSpecifications.hasRepositoryId(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasRepositoryId_navigatesThroughAnalysisRunPullRequestRepository() {
        Path<Object> analysisRunPath = mock(Path.class);
        Path<Object> pullRequestPath = mock(Path.class);
        Path<Object> repositoryPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("analysisRun")).thenReturn(analysisRunPath);
        when(analysisRunPath.get("pullRequest")).thenReturn(pullRequestPath);
        when(pullRequestPath.get("repository")).thenReturn(repositoryPath);
        when(repositoryPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 3L)).thenReturn(expected);

        Predicate predicate = AIReviewRunSpecifications.hasRepositoryId(3L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasStatus_returnsNullPredicateWhenNull() {
        assertThat(AIReviewRunSpecifications.hasStatus(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasStatus_buildsAnEqualityPredicate() {
        Path<Object> statusPath = mock(Path.class);
        when(root.get("status")).thenReturn(statusPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(statusPath, AIReviewRunStatus.FAILED)).thenReturn(expected);

        Predicate predicate = AIReviewRunSpecifications.hasStatus(AIReviewRunStatus.FAILED).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasProvider_returnsNullPredicateWhenNullOrBlank() {
        assertThat(AIReviewRunSpecifications.hasProvider(null).toPredicate(root, query, builder)).isNull();
        assertThat(AIReviewRunSpecifications.hasProvider("  ").toPredicate(root, query, builder)).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasProvider_buildsAnExactEqualityPredicate() {
        Path<Object> providerPath = mock(Path.class);
        when(root.get("provider")).thenReturn(providerPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(providerPath, "anthropic-claude")).thenReturn(expected);

        Predicate predicate = AIReviewRunSpecifications.hasProvider("anthropic-claude").toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(AIReviewRunSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(AIReviewRunSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void withFetchedAssociations_fetchesTheFullContextChainForAnEntityResultQuery() {
        Fetch<Object, Object> analysisRunFetch = mock(Fetch.class);
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) AIReviewRun.class);
        when(root.fetch("analysisRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(analysisRunFetch);
        when(analysisRunFetch.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AIReviewRunSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AIReviewRunSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) AIReviewRun.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        AIReviewRunFilter filter = new AIReviewRunFilter(
                1L, 2L, AIReviewRunStatus.COMPLETED, "anthropic-claude", Instant.EPOCH, Instant.now());

        org.springframework.data.jpa.domain.Specification<AIReviewRun> spec = AIReviewRunSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }
}
