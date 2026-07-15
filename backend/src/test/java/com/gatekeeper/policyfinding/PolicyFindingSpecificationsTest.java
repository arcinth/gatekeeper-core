package com.gatekeeper.policyfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.dto.PolicyFindingFilter;
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

class PolicyFindingSpecificationsTest {

    private final Root<PolicyFindingEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasAnalysisRunId_returnsNullPredicateWhenNull() {
        Predicate predicate = PolicyFindingSpecifications.hasAnalysisRunId(null).toPredicate(root, query, builder);

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

        Predicate predicate = PolicyFindingSpecifications.hasAnalysisRunId(5L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        assertThat(PolicyFindingSpecifications.hasRepositoryId(null).toPredicate(root, query, builder)).isNull();
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

        Predicate predicate = PolicyFindingSpecifications.hasRepositoryId(3L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasSeverity_returnsNullPredicateWhenNull() {
        assertThat(PolicyFindingSpecifications.hasSeverity(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasSeverity_buildsAnEqualityPredicate() {
        Path<Object> severityPath = mock(Path.class);
        when(root.get("severity")).thenReturn(severityPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(severityPath, PolicySeverity.CRITICAL)).thenReturn(expected);

        Predicate predicate = PolicyFindingSpecifications.hasSeverity(PolicySeverity.CRITICAL)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasCategory_returnsNullPredicateWhenNull() {
        assertThat(PolicyFindingSpecifications.hasCategory(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasCategory_buildsAnEqualityPredicate() {
        Path<Object> categoryPath = mock(Path.class);
        when(root.get("category")).thenReturn(categoryPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(categoryPath, PolicyCategory.CODE_QUALITY)).thenReturn(expected);

        Predicate predicate = PolicyFindingSpecifications.hasCategory(PolicyCategory.CODE_QUALITY)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRuleId_returnsNullPredicateWhenNullOrBlank() {
        assertThat(PolicyFindingSpecifications.hasRuleId(null).toPredicate(root, query, builder)).isNull();
        assertThat(PolicyFindingSpecifications.hasRuleId("  ").toPredicate(root, query, builder)).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasRuleId_buildsAnExactEqualityPredicate() {
        Path<Object> ruleIdPath = mock(Path.class);
        when(root.get("ruleId")).thenReturn(ruleIdPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(ruleIdPath, "TODO_COMMENT")).thenReturn(expected);

        Predicate predicate = PolicyFindingSpecifications.hasRuleId("TODO_COMMENT").toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(PolicyFindingSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(PolicyFindingSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void withFetchedAssociations_fetchesTheFullContextChainForAnEntityResultQuery() {
        Fetch<Object, Object> analysisRunFetch = mock(Fetch.class);
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) PolicyFindingEntity.class);
        when(root.fetch("analysisRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(analysisRunFetch);
        when(analysisRunFetch.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        PolicyFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        PolicyFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) PolicyFindingEntity.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        PolicyFindingFilter filter = new PolicyFindingFilter(1L, 2L, PolicySeverity.HIGH,
                PolicyCategory.MAINTAINABILITY, "TODO_COMMENT", Instant.EPOCH, Instant.now());

        org.springframework.data.jpa.domain.Specification<PolicyFindingEntity> spec =
                PolicyFindingSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }

    @Test
    void orderBySeverityRank_setsADescendingRankOrderForDescendingDirection() {
        Path<Object> severityPath = mock(Path.class);
        when(root.get("severity")).thenReturn(severityPath);
        when(query.getResultType()).thenReturn((Class) PolicyFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));
        Predicate conjunction = mock(Predicate.class);
        when(builder.conjunction()).thenReturn(conjunction);

        Predicate predicate = PolicyFindingSpecifications.orderBySeverityRank(Sort.Direction.DESC)
                .toPredicate(root, query, builder);

        // DESC severity (CRITICAL first) means ascending rank (CRITICAL=0 first).
        verify(builder).asc(any(Expression.class));
        verify(builder, never()).desc(any(Expression.class));
        assertThat(predicate).isSameAs(conjunction);
    }

    @Test
    void orderBySeverityRank_setsAnAscendingRankOrderForAscendingDirection() {
        Path<Object> severityPath = mock(Path.class);
        when(root.get("severity")).thenReturn(severityPath);
        when(query.getResultType()).thenReturn((Class) PolicyFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));

        PolicyFindingSpecifications.orderBySeverityRank(Sort.Direction.ASC).toPredicate(root, query, builder);

        verify(builder).desc(any(Expression.class));
        verify(builder, never()).asc(any(Expression.class));
    }

    @Test
    void orderBySeverityRank_skipsForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        PolicyFindingSpecifications.orderBySeverityRank(Sort.Direction.DESC).toPredicate(root, query, builder);

        verify(builder, never()).selectCase();
        verify(query, never()).orderBy(any(jakarta.persistence.criteria.Order.class));
    }
}
