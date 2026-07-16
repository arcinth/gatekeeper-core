package com.gatekeeper.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.verdict.dto.VerdictFilter;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class VerdictQueryServiceTest {

    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final VerdictReasonRepository verdictReasonRepository = mock(VerdictReasonRepository.class);
    private final VerdictQueryService service = new VerdictQueryService(verdictRepository, verdictReasonRepository);

    @Test
    void findPage_enrichesEachRowWithItsReasonsTotalFromABatchedCountQuery() {
        Verdict verdict = verdictWithContext(1L, VerdictOutcome.BLOCKED);
        Page<Verdict> page = new PageImpl<>(List.of(verdict));
        when(verdictRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(verdictReasonRepository.countByVerdictIdIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[] {1L, 2L}));

        Page<?> result = service.findPage(emptyFilter(), PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findPage_skipsTheBatchCountQueryForAnEmptyPage() {
        Page<Verdict> emptyPage = new PageImpl<>(List.of());
        when(verdictRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        service.findPage(emptyFilter(), PageRequest.of(0, 20));

        verify(verdictReasonRepository, never()).countByVerdictIdIn(any());
    }

    @Test
    void findDetailByIdOrThrow_returnsTheMappedResponseWithReasonsWhenTheVerdictExists() {
        Verdict verdict = verdictWithContext(1L, VerdictOutcome.BLOCKED);
        when(verdictRepository.findWithContextById(1L)).thenReturn(Optional.of(verdict));
        VerdictReasonEntity reason = VerdictReasonEntity.builder()
                .verdict(verdict).ruleId("CRITICAL_SECURITY_FINDING").blocking(true).message("blocked!").build();
        when(verdictReasonRepository.findByVerdictIdOrderById(1L)).thenReturn(List.of(reason));

        var result = service.findDetailByIdOrThrow(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.outcome()).isEqualTo(VerdictOutcome.BLOCKED);
        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0).ruleId()).isEqualTo("CRITICAL_SECURITY_FINDING");
    }

    @Test
    void findDetailByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(verdictRepository.findWithContextById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetailByIdOrThrow(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private VerdictFilter emptyFilter() {
        return new VerdictFilter(null, null, null, null, null);
    }

    private Verdict verdictWithContext(Long id, VerdictOutcome outcome) {
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
        Verdict verdict = Verdict.builder().analysisRun(analysisRun).outcome(outcome).build();
        ReflectionTestUtils.setField(verdict, "id", id);
        return verdict;
    }
}
