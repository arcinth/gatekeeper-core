package com.gatekeeper.aireviewrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.dto.AIReviewRunFilter;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class AIReviewRunQueryServiceTest {

    private final AIReviewRunRepository aiReviewRunRepository = mock(AIReviewRunRepository.class);
    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final AIReviewRunQueryService service =
            new AIReviewRunQueryService(aiReviewRunRepository, aiReviewFindingRepository);

    @Test
    void findPage_enrichesEachRowWithItsFindingsTotalFromABatchedCountQuery() {
        AIReviewRun run = runWithContext(1L, AIReviewRunStatus.COMPLETED);
        Page<AIReviewRun> page = new PageImpl<>(List.of(run));
        when(aiReviewRunRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(aiReviewFindingRepository.countByAiReviewRunIdIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[] {1L, 3L}));

        Page<?> result = service.findPage(emptyFilter(), PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findPage_skipsTheBatchCountQueryForAnEmptyPage() {
        Page<AIReviewRun> emptyPage = new PageImpl<>(List.of());
        when(aiReviewRunRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        service.findPage(emptyFilter(), PageRequest.of(0, 20));

        org.mockito.Mockito.verify(aiReviewFindingRepository, org.mockito.Mockito.never())
                .countByAiReviewRunIdIn(any());
    }

    @Test
    void findDetailByIdOrThrow_returnsTheMappedResponseWithConfidenceBreakdownWhenTheRunExists() {
        AIReviewRun run = runWithContext(1L, AIReviewRunStatus.COMPLETED);
        when(aiReviewRunRepository.findWithContextById(1L)).thenReturn(Optional.of(run));
        when(aiReviewFindingRepository.countByConfidenceForAiReviewRun(1L))
                .thenReturn(List.<Object[]>of(new Object[] {AIReviewConfidence.HIGH, 2L}));

        var result = service.findDetailByIdOrThrow(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.findingsByConfidence()).containsEntry(AIReviewConfidence.HIGH, 2L);
    }

    @Test
    void findDetailByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(aiReviewRunRepository.findWithContextById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetailByIdOrThrow(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private AIReviewRunFilter emptyFilter() {
        return new AIReviewRunFilter(null, null, null, null, null, null);
    }

    private AIReviewRun runWithContext(Long id, AIReviewRunStatus status) {
        Repository repository = Repository.builder().name("core").fullName("org/core").build();
        ReflectionTestUtils.setField(repository, "id", 100L);
        PullRequest pullRequest = PullRequest.builder()
                .repository(repository)
                .number(7)
                .title("Add feature")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha("sha")
                .status(PullRequestStatus.OPEN)
                .build();
        AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).commitSha("sha-1").build();
        ReflectionTestUtils.setField(analysisRun, "id", 9L);
        AIReviewRun run = AIReviewRun.builder()
                .analysisRun(analysisRun)
                .status(status)
                .provider("anthropic-claude")
                .model("claude-opus-4-6")
                .promptVersion("v1")
                .summary("summary")
                .build();
        ReflectionTestUtils.setField(run, "id", id);
        return run;
    }
}
