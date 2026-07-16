package com.gatekeeper.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.verdict.dto.VerdictFilter;
import com.gatekeeper.verdictengine.VerdictOutcome;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerdictSpecificationsTest {

    private final Root<Verdict> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasAnalysisRunId_returnsNullPredicateWhenNull() {
        Predicate predicate = VerdictSpecifications.hasAnalysisRunId(null).toPredicate(root, query, builder);

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

        Predicate predicate = VerdictSpecifications.hasAnalysisRunId(5L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        assertThat(VerdictSpecifications.hasRepositoryId(null).toPredicate(root, query, builder)).isNull();
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

        Predicate predicate = VerdictSpecifications.hasRepositoryId(3L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasOutcome_returnsNullPredicateWhenNull() {
        assertThat(VerdictSpecifications.hasOutcome(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasOutcome_buildsAnEqualityPredicate() {
        Path<Object> outcomePath = mock(Path.class);
        when(root.get("outcome")).thenReturn(outcomePath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(outcomePath, VerdictOutcome.BLOCKED)).thenReturn(expected);

        Predicate predicate = VerdictSpecifications.hasOutcome(VerdictOutcome.BLOCKED).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(VerdictSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(VerdictSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void withFetchedAssociations_fetchesTheFullContextChainForAnEntityResultQuery() {
        Fetch<Object, Object> analysisRunFetch = mock(Fetch.class);
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) Verdict.class);
        when(root.fetch("analysisRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(analysisRunFetch);
        when(analysisRunFetch.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        VerdictSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        VerdictSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) Verdict.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        VerdictFilter filter = new VerdictFilter(1L, 2L, VerdictOutcome.APPROVED, Instant.EPOCH, Instant.now());

        org.springframework.data.jpa.domain.Specification<Verdict> spec = VerdictSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }
}
