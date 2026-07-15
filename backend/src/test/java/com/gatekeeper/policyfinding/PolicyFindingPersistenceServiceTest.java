package com.gatekeeper.policyfinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicyResult;
import com.gatekeeper.policy.PolicySeverity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PolicyFindingPersistenceServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final PolicyFindingPersistenceService service =
            new PolicyFindingPersistenceService(analysisRunService, policyFindingRepository);

    @Test
    void persistCompletedResult_savesEveryFindingAndMarksTheRunCompleted() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        PolicyFinding findingOne = new PolicyFinding("TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW, "a.txt", 1, "m1", "r1");
        PolicyFinding findingTwo = new PolicyFinding("FIXME_COMMENT", PolicyCategory.CODE_QUALITY, PolicySeverity.MEDIUM, "b.txt", 2, "m2", "r2");
        PolicyResult result = new PolicyResult(ANALYSIS_RUN_ID, List.of(findingOne, findingTwo), 2, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PolicyFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(policyFindingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(PolicyFindingEntity::getRuleId)
                .containsExactly("TODO_COMMENT", "FIXME_COMMENT");
        assertThat(captor.getValue()).allSatisfy(entity -> assertThat(entity.getAnalysisRun()).isSameAs(analysisRun));

        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_persistsAnEmptyResultAsZeroFindingsStillMarkingCompleted() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        PolicyResult cleanResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, cleanResult);

        verify(policyFindingRepository).saveAll(anyList());
        verify(analysisRunService).markCompleted(analysisRun);
    }
}
