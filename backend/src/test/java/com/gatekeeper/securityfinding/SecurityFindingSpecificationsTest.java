package com.gatekeeper.securityfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.dto.SecurityFindingFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SecurityFindingSpecificationsTest {

    private final Root<SecurityFindingEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void hasAnalysisRunId_returnsNullPredicateWhenNull() {
        Predicate predicate = SecurityFindingSpecifications.hasAnalysisRunId(null).toPredicate(root, query, builder);

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

        Predicate predicate = SecurityFindingSpecifications.hasAnalysisRunId(5L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRepositoryId_returnsNullPredicateWhenNull() {
        assertThat(SecurityFindingSpecifications.hasRepositoryId(null).toPredicate(root, query, builder)).isNull();
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

        Predicate predicate = SecurityFindingSpecifications.hasRepositoryId(3L).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasSeverity_returnsNullPredicateWhenNull() {
        assertThat(SecurityFindingSpecifications.hasSeverity(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasSeverity_buildsAnEqualityPredicate() {
        Path<Object> severityPath = mock(Path.class);
        when(root.get("severity")).thenReturn(severityPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(severityPath, SecuritySeverity.CRITICAL)).thenReturn(expected);

        Predicate predicate = SecurityFindingSpecifications.hasSeverity(SecuritySeverity.CRITICAL)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasCategory_returnsNullPredicateWhenNull() {
        assertThat(SecurityFindingSpecifications.hasCategory(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void hasCategory_buildsAnEqualityPredicate() {
        Path<Object> categoryPath = mock(Path.class);
        when(root.get("category")).thenReturn(categoryPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(categoryPath, SecurityCategory.SECRETS_EXPOSURE)).thenReturn(expected);

        Predicate predicate = SecurityFindingSpecifications.hasCategory(SecurityCategory.SECRETS_EXPOSURE)
                .toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void hasRuleId_returnsNullPredicateWhenNullOrBlank() {
        assertThat(SecurityFindingSpecifications.hasRuleId(null).toPredicate(root, query, builder)).isNull();
        assertThat(SecurityFindingSpecifications.hasRuleId("  ").toPredicate(root, query, builder)).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void hasRuleId_buildsAnExactEqualityPredicate() {
        Path<Object> ruleIdPath = mock(Path.class);
        when(root.get("ruleId")).thenReturn(ruleIdPath);
        Predicate expected = mock(Predicate.class);
        when(builder.equal(ruleIdPath, "HARDCODED_SECRET")).thenReturn(expected);

        Predicate predicate = SecurityFindingSpecifications.hasRuleId("HARDCODED_SECRET").toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void currentOnly_returnsNullPredicateWhenDisabled() {
        assertThat(SecurityFindingSpecifications.currentOnly(false).toPredicate(root, query, builder)).isNull();
        verifyNoInteractions(builder);
    }

    @Test
    void currentOnly_restrictsToTheLatestRunForTheSamePullRequestAndAnOpenPullRequest() {
        Path<Object> analysisRunPath = mock(Path.class);
        Path<Object> pullRequestPath = mock(Path.class);
        Path<Object> pullRequestIdPath = mock(Path.class);
        Path<Object> analysisRunIdPath = mock(Path.class);
        Path<Object> statusPath = mock(Path.class);
        when(root.get("analysisRun")).thenReturn(analysisRunPath);
        when(analysisRunPath.get("pullRequest")).thenReturn(pullRequestPath);
        when(pullRequestPath.get("id")).thenReturn(pullRequestIdPath);
        when(analysisRunPath.get("id")).thenReturn(analysisRunIdPath);
        when(pullRequestPath.get("status")).thenReturn(statusPath);

        Subquery<Long> subquery = mock(Subquery.class);
        Root<AnalysisRun> subqueryRoot = mock(Root.class);
        Path<Object> subqueryPullRequestPath = mock(Path.class);
        Path<Object> subqueryPullRequestIdPath = mock(Path.class);
        Path<Long> subqueryRunIdPath = mock(Path.class);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(AnalysisRun.class)).thenReturn(subqueryRoot);
        when(subqueryRoot.get("pullRequest")).thenReturn(subqueryPullRequestPath);
        when(subqueryPullRequestPath.get("id")).thenReturn(subqueryPullRequestIdPath);
        when(subqueryRoot.<Long>get("id")).thenReturn(subqueryRunIdPath);
        Expression<Long> maxExpression = mock(Expression.class);
        when(builder.max(subqueryRunIdPath)).thenReturn(maxExpression);
        when(subquery.select(maxExpression)).thenReturn(subquery);
        Predicate correlation = mock(Predicate.class);
        when(builder.equal(subqueryPullRequestIdPath, pullRequestIdPath)).thenReturn(correlation);
        when(subquery.where(correlation)).thenReturn(subquery);

        Predicate isLatestRun = mock(Predicate.class);
        when(builder.equal(analysisRunIdPath, subquery)).thenReturn(isLatestRun);
        Predicate isOpen = mock(Predicate.class);
        when(builder.equal(statusPath, PullRequestStatus.OPEN)).thenReturn(isOpen);
        Predicate combined = mock(Predicate.class);
        when(builder.and(isLatestRun, isOpen)).thenReturn(combined);

        Predicate predicate = SecurityFindingSpecifications.currentOnly(true).toPredicate(root, query, builder);

        assertThat(predicate).isSameAs(combined);
    }

    @Test
    void createdFrom_returnsNullPredicateWhenNull() {
        assertThat(SecurityFindingSpecifications.createdFrom(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void createdTo_returnsNullPredicateWhenNull() {
        assertThat(SecurityFindingSpecifications.createdTo(null).toPredicate(root, query, builder)).isNull();
    }

    @Test
    void withFetchedAssociations_fetchesTheFullContextChainForAnEntityResultQuery() {
        Fetch<Object, Object> analysisRunFetch = mock(Fetch.class);
        Fetch<Object, Object> pullRequestFetch = mock(Fetch.class);
        when(query.getResultType()).thenReturn((Class) SecurityFindingEntity.class);
        when(root.fetch("analysisRun", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(analysisRunFetch);
        when(analysisRunFetch.fetch("pullRequest", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(pullRequestFetch);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        SecurityFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(pullRequestFetch).fetch("repository", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    void withFetchedAssociations_skipsFetchingForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        SecurityFindingSpecifications.withFetchedAssociations().toPredicate(root, query, builder);

        verify(root, never()).fetch(any(String.class), any());
    }

    @Test
    void matching_composesAllProvidedFiltersWithoutThrowing() {
        when(query.getResultType()).thenReturn((Class) SecurityFindingEntity.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));
        when(builder.and(any(), any())).thenReturn(mock(Predicate.class));
        SecurityFindingFilter filter = new SecurityFindingFilter(1L, 2L, SecuritySeverity.HIGH,
                SecurityCategory.INSECURE_CRYPTOGRAPHY, "HARDCODED_SECRET", Instant.EPOCH, Instant.now(), false);

        org.springframework.data.jpa.domain.Specification<SecurityFindingEntity> spec =
                SecurityFindingSpecifications.matching(filter);

        assertThat(spec).isNotNull();
    }

    @Test
    void orderBySeverityRank_setsADescendingRankOrderForDescendingDirection() {
        Path<Object> severityPath = mock(Path.class);
        when(root.get("severity")).thenReturn(severityPath);
        when(query.getResultType()).thenReturn((Class) SecurityFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));
        Predicate conjunction = mock(Predicate.class);
        when(builder.conjunction()).thenReturn(conjunction);

        Predicate predicate = SecurityFindingSpecifications.orderBySeverityRank(Sort.Direction.DESC)
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
        when(query.getResultType()).thenReturn((Class) SecurityFindingEntity.class);
        CriteriaBuilder.Case<Integer> caseExpression = mock(CriteriaBuilder.Case.class);
        when(builder.<Integer>selectCase()).thenReturn(caseExpression);
        when(caseExpression.when(any(), any(Integer.class))).thenReturn(caseExpression);
        when(caseExpression.otherwise(any(Integer.class))).thenReturn(mock(Expression.class));

        SecurityFindingSpecifications.orderBySeverityRank(Sort.Direction.ASC).toPredicate(root, query, builder);

        verify(builder).desc(any(Expression.class));
        verify(builder, never()).asc(any(Expression.class));
    }

    @Test
    void orderBySeverityRank_skipsForACountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
        when(builder.conjunction()).thenReturn(mock(Predicate.class));

        SecurityFindingSpecifications.orderBySeverityRank(Sort.Direction.DESC).toPredicate(root, query, builder);

        verify(builder, never()).selectCase();
        verify(query, never()).orderBy(any(jakarta.persistence.criteria.Order.class));
    }
}
