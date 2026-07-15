package com.gatekeeper.orchestration;

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
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecurityResult;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalysisResultPersistenceServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final AnalysisResultPersistenceService service = new AnalysisResultPersistenceService(
            analysisRunService, policyFindingRepository, securityFindingRepository);

    @Test
    void persistCompletedResult_savesFindingsFromBothEnginesAndMarksTheRunCompleted() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        PolicyFinding policyFinding = new PolicyFinding(
                "TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW, "a.txt", 1, "m1", "r1");
        PolicyResult policyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(policyFinding), 1, Instant.now());
        SecurityFinding securityFinding = new SecurityFinding(
                "HARDCODED_SECRET", SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.CRITICAL, "b.txt", 2, "m2", "r2");
        SecurityResult securityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(securityFinding), 1, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, policyResult, securityResult);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PolicyFindingEntity>> policyCaptor = ArgumentCaptor.forClass(List.class);
        verify(policyFindingRepository).saveAll(policyCaptor.capture());
        assertThat(policyCaptor.getValue()).hasSize(1);
        assertThat(policyCaptor.getValue().get(0).getRuleId()).isEqualTo("TODO_COMMENT");
        assertThat(policyCaptor.getValue()).allSatisfy(entity -> assertThat(entity.getAnalysisRun()).isSameAs(analysisRun));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SecurityFindingEntity>> securityCaptor = ArgumentCaptor.forClass(List.class);
        verify(securityFindingRepository).saveAll(securityCaptor.capture());
        assertThat(securityCaptor.getValue()).hasSize(1);
        assertThat(securityCaptor.getValue().get(0).getRuleId()).isEqualTo("HARDCODED_SECRET");
        assertThat(securityCaptor.getValue()).allSatisfy(entity -> assertThat(entity.getAnalysisRun()).isSameAs(analysisRun));

        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_persistsEmptyResultsFromBothEnginesAsZeroFindingsStillMarkingCompleted() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        PolicyResult cleanPolicyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());
        SecurityResult cleanSecurityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, cleanPolicyResult, cleanSecurityResult);

        verify(policyFindingRepository).saveAll(anyList());
        verify(securityFindingRepository).saveAll(anyList());
        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_savesOnlyPolicyFindingsWhenSecurityEngineFoundNothing() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        PolicyFinding policyFinding = new PolicyFinding(
                "FIXME_COMMENT", PolicyCategory.CODE_QUALITY, PolicySeverity.MEDIUM, "a.txt", 1, "m", "r");
        PolicyResult policyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(policyFinding), 1, Instant.now());
        SecurityResult cleanSecurityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, policyResult, cleanSecurityResult);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PolicyFindingEntity>> policyCaptor = ArgumentCaptor.forClass(List.class);
        verify(policyFindingRepository).saveAll(policyCaptor.capture());
        assertThat(policyCaptor.getValue()).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SecurityFindingEntity>> securityCaptor = ArgumentCaptor.forClass(List.class);
        verify(securityFindingRepository).saveAll(securityCaptor.capture());
        assertThat(securityCaptor.getValue()).isEmpty();

        verify(analysisRunService).markCompleted(analysisRun);
    }
}
