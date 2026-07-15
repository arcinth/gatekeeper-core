package com.gatekeeper.policyfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.dto.PolicyFindingFilter;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyFindingQueryServiceTest {

    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final PolicyFindingQueryService service = new PolicyFindingQueryService(policyFindingRepository);

    @Test
    void findByIdOrThrow_returnsTheMappedResponseWhenTheFindingExists() {
        PolicyFindingEntity entity = findingWithContext(1L, "TODO_COMMENT", PolicySeverity.LOW);
        when(policyFindingRepository.findWithContextById(1L)).thenReturn(Optional.of(entity));

        var result = service.findByIdOrThrow(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.ruleId()).isEqualTo("TODO_COMMENT");
        assertThat(result.repositoryFullName()).isEqualTo("org/core");
    }

    @Test
    void findByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(policyFindingRepository.findWithContextById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdOrThrow(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findPage_passesTheUnmodifiedPageableThroughWhenNoSeveritySortIsRequested() {
        Page<PolicyFindingEntity> page = new PageImpl<>(List.of(findingWithContext(1L, "TODO_COMMENT", PolicySeverity.LOW)));
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(policyFindingRepository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(page);

        var result = service.findPage(emptyFilter(), pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findPage_stripsSortAndUsesTheSeverityRankSpecificationWhenSeveritySortIsRequested() {
        Page<PolicyFindingEntity> page = new PageImpl<>(List.of(findingWithContext(1L, "TODO_COMMENT", PolicySeverity.LOW)));
        Pageable requested = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "severity"));
        when(policyFindingRepository.findAll(any(Specification.class),
                argThat((Pageable p) -> p.getPageNumber() == 1 && p.getPageSize() == 10 && p.getSort().isUnsorted())))
                .thenReturn(page);

        var result = service.findPage(emptyFilter(), requested);

        assertThat(result.getContent()).hasSize(1);
    }

    private PolicyFindingFilter emptyFilter() {
        return new PolicyFindingFilter(null, null, null, null, null, null, null);
    }

    private PolicyFindingEntity findingWithContext(Long id, String ruleId, PolicySeverity severity) {
        Repository repository = Repository.builder().name("core").fullName("org/core").build();
        ReflectionTestUtils.setField(repository, "id", 100L);
        PullRequest pullRequest = PullRequest.builder()
                .repository(repository)
                .number(21)
                .title("Add example")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha("sha")
                .status(com.gatekeeper.pullrequest.PullRequestStatus.OPEN)
                .build();
        AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).commitSha("sha-1").build();
        ReflectionTestUtils.setField(analysisRun, "id", 9L);
        PolicyFindingEntity entity = PolicyFindingEntity.builder()
                .analysisRun(analysisRun)
                .ruleId(ruleId)
                .category(PolicyCategory.MAINTAINABILITY)
                .severity(severity)
                .filePath("src/Example.java")
                .lineNumber(1)
                .message("message")
                .recommendation("recommendation")
                .build();
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
