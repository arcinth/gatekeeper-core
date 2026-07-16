package com.gatekeeper.aireviewfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class AIReviewFindingSpecificationsTest {

    private final Root<AIReviewFindingEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasAiReviewRunId_returnsNullPredicateWhenNull() {
        Predicate predicate = AIReviewFindingSpecifications.hasAiReviewRunId(null).toPredicate(root, query, builder);

        assertThat(predicate).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasAiReviewRunId_buildsAnEqualityPredicate() {
        Path<Object> aiReviewRunPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("aiReviewRun")).thenReturn(aiReviewRunPath);
        when(aiReviewRunPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 5L)).thenReturn(expected);

        Predicate predicate = AIReviewFindingSpecifications.hasAiReviewRunId(5L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasAnalysisRunId_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.hasAnalysisRunId(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasAnalysisRunId_navigatesThroughAiReviewRunAnalysisRun() {
        Path<Object> aiReviewRunPath = mock(Path.class);
        Path<Object> analysisRunPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("aiReviewRun")).thenReturn(aiReviewRunPath);
        when(aiReviewRunPath.get("analysisRun")).thenReturn(analysisRunPath);
        when(analysisRunPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 9L)).thenReturn(expected);

        Predicate predicate = AIReviewFindingSpecifications.hasAnalysisRunId(9L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.hasRepositoryId(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasRepositoryId_navigatesThroughAiReviewRunAnalysisRunPullRequestRepository() {
        Path<Object> aiReviewRunPath = mock(Path.class);
        Path<Object> analysisRunPath = mock(Path.class);
        Path<Object> pullRequestPath = mock(Path.class);
        Path<Object> repositoryPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("aiReviewRun")).thenReturn(aiReviewRunPath);
        when(aiReviewRunPath.get("analysisRun")).thenReturn(analysisRunPath);
        when(analysisRunPath.get("pullRequest")).thenReturn(pullRequestPath);
        when(pullRequestPath.get("repository")).thenReturn(repositoryPath);
        when(repositoryPath.get("id")).thenReturn(idPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(idPath, 3L)).thenReturn(expected);

        Predicate predicate = AIReviewFindingSpecifications.hasRepositoryId(3L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasConfidence_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.hasConfidence(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasConfidence_buildsAnEqualityPredicate() {
        Path<Object> confidencePath = mock(Path.class);
        when(root.get("confidence")).thenReturn(confidencePath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(confidencePath, AIReviewConfidence.HIGH)).thenReturn(expected);

        Predicate predicate = AIReviewFindingSpecifications.hasConfidence(AIReviewConfidence.HIGH)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasType_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.hasType(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasType_buildsAnEqualityPredicate() {
        Path<Object> typePath = mock(Path.class);
        when(root.get("type")).thenReturn(typePath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(typePath, AIReviewFindingType.POTENTIAL_BUG)).thenReturn(expected);

        Predicate predicate = AIReviewFindingSpecifications.hasType(AIReviewFindingType.POTENTIAL_BUG)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(AIReviewFindingSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void withFetchedAssociations_fetchesTheFullContextChainForAnEntityResultQuery() {
        Fetch<Object, Object> aiReviewRunFetch = mock(Fetch.class);
        Fetch<Object, Object> analysisRunFetch = mock(Fetch.class);
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) AIReviewFindingEntity.class);
        when(root.fetch("aiReviewRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(aiReviewRunFetch);
        when(aiReviewRunFetch.fetch("analysisRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(analysisRunFetch);
        when(analysisRunFetch.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AIReviewFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AIReviewFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) AIReviewFindingEntity.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        AIReviewFindingFilter filter = new AIReviewFindingFilter(
                1L, 2L, 3L, AIReviewConfidence.HIGH, AIReviewFindingType.POTENTIAL_BUG, Instant.EPOCH, Instant.now());

        org.springframework.data.jpa.domain.Specification<AIReviewFindingEntity> spec =
                AIReviewFindingSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }

    @Test
    void orderByConfidenceRank_setsADescendingRankOrderForDescendingDirection() {
        Path<Object> confidencePath = mock(Path.class);
        when(root.get("confidence")).thenReturn(confidencePath);
        when(query.getResultType()).thenReturn((Class) AIReviewFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));
        Predicate conjunction = mock(Predicate.class);
        when(builder.conjunction()).thenReturn(conjunction);

        Predicate predicate = AIReviewFindingSpecifications.orderByConfidenceRank(Sort.Direction.DESC)
                .toPredicate(root, query, builder);

        // DESC confidence (HIGH first) means ascending rank (HIGH=0 first).
        verify(builder).asc(any(Expression.class));
        verify(builder, never()).desc(any(Expression.class));
        assertThat(predicate).isSameAs(conjunction);
    }

    @Test
    void orderByConfidenceRank_setsAnAscendingRankOrderForAscendingDirection() {
        Path<Object> confidencePath = mock(Path.class);
        when(root.get("confidence")).thenReturn(confidencePath);
        when(query.getResultType()).thenReturn((Class) AIReviewFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));

        AIReviewFindingSpecifications.orderByConfidenceRank(Sort.Direction.ASC).toPredicate(root, query, builder);

        verify(builder).desc(any(Expression.class));
        verify(builder, never()).asc(any(Expression.class));
    }

    @Test
    void orderByConfidenceRank_skipsForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        AIReviewFindingSpecifications.orderByConfidenceRank(Sort.Direction.DESC).toPredicate(root, query, builder);

        verify(builder, never()).selectCase();
        verify(query, never()).orderBy(any(jakarta.persistence.criteria.Order.class));
    }
}
