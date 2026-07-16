package com.gatekeeper.aireviewfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingFilter;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class AIReviewFindingQueryServiceTest {

    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final AIReviewFindingQueryService service = new AIReviewFindingQueryService(aiReviewFindingRepository);

    @Test
    void findByIdOrThrow_returnsTheMappedResponseWhenTheFindingExists() {
        AIReviewFindingEntity entity = findingWithContext(1L, AIReviewConfidence.HIGH);
        when(aiReviewFindingRepository.findWithContextById(1L)).thenReturn(Optional.of(entity));

        var result = service.findByIdOrThrow(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.confidence()).isEqualTo(AIReviewConfidence.HIGH);
        assertThat(result.repositoryFullName()).isEqualTo("org/core");
    }

    @Test
    void findByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(aiReviewFindingRepository.findWithContextById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdOrThrow(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findPage_passesTheUnmodifiedPageableThroughWhenNoConfidenceSortIsRequested() {
        Page<AIReviewFindingEntity> page = new PageImpl<>(List.of(findingWithContext(1L, AIReviewConfidence.HIGH)));
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(aiReviewFindingRepository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(page);

        var result = service.findPage(emptyFilter(), pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findPage_stripsSortAndUsesTheConfidenceRankSpecificationWhenConfidenceSortIsRequested() {
        Page<AIReviewFindingEntity> page = new PageImpl<>(List.of(findingWithContext(1L, AIReviewConfidence.HIGH)));
        Pageable requested = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "confidence"));
        when(aiReviewFindingRepository.findAll(any(Specification.class),
                argThat((Pageable p) -> p.getPageNumber() == 1 && p.getPageSize() == 10 && p.getSort().isUnsorted())))
                .thenReturn(page);

        var result = service.findPage(emptyFilter(), requested);

        assertThat(result.getContent()).hasSize(1);
    }

    private AIReviewFindingFilter emptyFilter() {
        return new AIReviewFindingFilter(null, null, null, null, null, null, null);
    }

    private AIReviewFindingEntity findingWithContext(Long id, AIReviewConfidence confidence) {
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
                .status(PullRequestStatus.OPEN)
                .build();
        AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).commitSha("sha-1").build();
        ReflectionTestUtils.setField(analysisRun, "id", 9L);
        AIReviewRun aiReviewRun = AIReviewRun.builder()
                .analysisRun(analysisRun)
                .status(AIReviewRunStatus.COMPLETED)
                .provider("anthropic-claude")
                .model("claude-opus-4-6")
                .promptVersion("v1")
                .build();
        ReflectionTestUtils.setField(aiReviewRun, "id", 5L);
        AIReviewFindingEntity entity = AIReviewFindingEntity.builder()
                .aiReviewRun(aiReviewRun)
                .type(AIReviewFindingType.POTENTIAL_BUG)
                .confidence(confidence)
                .filePath("src/Example.java")
                .lineNumber(1)
                .message("message")
                .recommendation("recommendation")
                .build();
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
