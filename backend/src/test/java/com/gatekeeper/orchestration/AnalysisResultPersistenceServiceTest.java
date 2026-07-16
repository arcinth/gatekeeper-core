package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecurityResult;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonEntity;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictEngine;
import com.gatekeeper.verdictengine.VerdictOutcome;
import com.gatekeeper.verdictengine.VerdictReason;
import com.gatekeeper.verdictengine.VerdictResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisResultPersistenceServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final VerdictEngine verdictEngine = mock(VerdictEngine.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final VerdictReasonRepository verdictReasonRepository = mock(VerdictReasonRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AnalysisResultPersistenceService service = new AnalysisResultPersistenceService(
            analysisRunService, policyFindingRepository, securityFindingRepository,
            verdictEngine, verdictRepository, verdictReasonRepository, eventPublisher);

    @Test
    void persistCompletedResult_savesFindingsFromBothEnginesAndMarksTheRunCompleted() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        stubApprovedVerdict(analysisRun);
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
    void persistCompletedResult_evaluatesTheVerdictFromTheSamePolicyAndSecurityResultsAlreadyInHand() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        stubApprovedVerdict(analysisRun);
        PolicyResult policyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now());
        SecurityResult securityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, policyResult, securityResult);

        ArgumentCaptor<VerdictContext> contextCaptor = ArgumentCaptor.forClass(VerdictContext.class);
        verify(verdictEngine).evaluate(contextCaptor.capture());
        assertThat(contextCaptor.getValue().analysisRunId()).isEqualTo(ANALYSIS_RUN_ID);
        assertThat(contextCaptor.getValue().repositoryFullName()).isEqualTo("org/core");
        // No re-querying: VerdictContext is built directly from the PolicyResult/SecurityResult
        // this method already received, not a fresh database read.
        assertThat(contextCaptor.getValue().policyFindings()).isSameAs(policyResult.findings());
        assertThat(contextCaptor.getValue().securityFindings()).isSameAs(securityResult.findings());
    }

    @Test
    void persistCompletedResult_savesTheVerdictAndItsReasonsBeforeMarkingTheRunCompleted() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        VerdictReason blockingReason = new VerdictReason("CRITICAL_SECURITY_FINDING", "blocked!", true);
        VerdictResult verdictResult = new VerdictResult(ANALYSIS_RUN_ID, VerdictOutcome.BLOCKED,
                List.of(blockingReason), Instant.now());
        when(verdictEngine.evaluate(any())).thenReturn(verdictResult);
        Verdict savedVerdict = Verdict.builder().analysisRun(analysisRun).outcome(VerdictOutcome.BLOCKED).build();
        when(verdictRepository.save(any())).thenReturn(savedVerdict);

        service.persistCompletedResult(ANALYSIS_RUN_ID,
                new PolicyResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()),
                new SecurityResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()));

        ArgumentCaptor<Verdict> verdictCaptor = ArgumentCaptor.forClass(Verdict.class);
        verify(verdictRepository).save(verdictCaptor.capture());
        assertThat(verdictCaptor.getValue().getOutcome()).isEqualTo(VerdictOutcome.BLOCKED);
        assertThat(verdictCaptor.getValue().getAnalysisRun()).isSameAs(analysisRun);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VerdictReasonEntity>> reasonsCaptor = ArgumentCaptor.forClass(List.class);
        verify(verdictReasonRepository).saveAll(reasonsCaptor.capture());
        assertThat(reasonsCaptor.getValue()).hasSize(1);
        assertThat(reasonsCaptor.getValue().get(0).getRuleId()).isEqualTo("CRITICAL_SECURITY_FINDING");
        assertThat(reasonsCaptor.getValue().get(0).isBlocking()).isTrue();
        assertThat(reasonsCaptor.getValue().get(0).getVerdict()).isSameAs(savedVerdict);

        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_savesAnApprovedVerdictWithNoReasonsWhenNothingBlocks() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        VerdictResult verdictResult = new VerdictResult(ANALYSIS_RUN_ID, VerdictOutcome.APPROVED, List.of(), Instant.now());
        when(verdictEngine.evaluate(any())).thenReturn(verdictResult);
        when(verdictRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.persistCompletedResult(ANALYSIS_RUN_ID,
                new PolicyResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()),
                new SecurityResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()));

        ArgumentCaptor<Verdict> verdictCaptor = ArgumentCaptor.forClass(Verdict.class);
        verify(verdictRepository).save(verdictCaptor.capture());
        assertThat(verdictCaptor.getValue().getOutcome()).isEqualTo(VerdictOutcome.APPROVED);
        verify(verdictReasonRepository).saveAll(anyList());
        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_whenVerdictEngineThrows_propagatesAndNeverMarksTheRunCompleted() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(verdictEngine.evaluate(any())).thenThrow(new IllegalStateException("simulated verdict engine bug"));

        assertThatThrownBy(() -> service.persistCompletedResult(ANALYSIS_RUN_ID,
                new PolicyResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()),
                new SecurityResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated verdict engine bug");

        // A verdict-engine failure must never leave a run silently COMPLETED without a
        // verdict - it propagates so the whole @Transactional method (and its caller's
        // transaction) rolls back, and no verdict rows are written either.
        verify(analysisRunService, never()).markCompleted(any());
        verify(verdictRepository, never()).save(any());
        verify(verdictReasonRepository, never()).saveAll(anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void persistCompletedResult_publishesVerdictProducedEventOnlyAfterMarkingTheRunCompleted() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        stubApprovedVerdict(analysisRun);

        service.persistCompletedResult(ANALYSIS_RUN_ID,
                new PolicyResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()),
                new SecurityResult(ANALYSIS_RUN_ID, List.of(), 0, Instant.now()));

        ArgumentCaptor<VerdictProducedEvent> eventCaptor = ArgumentCaptor.forClass(VerdictProducedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().analysisRunId()).isEqualTo(ANALYSIS_RUN_ID);
    }

    @Test
    void persistCompletedResult_persistsEmptyResultsFromBothEnginesAsZeroFindingsStillMarkingCompleted() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        stubApprovedVerdict(analysisRun);
        PolicyResult cleanPolicyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());
        SecurityResult cleanSecurityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, cleanPolicyResult, cleanSecurityResult);

        verify(policyFindingRepository).saveAll(anyList());
        verify(securityFindingRepository).saveAll(anyList());
        verify(analysisRunService).markCompleted(analysisRun);
    }

    @Test
    void persistCompletedResult_savesOnlyPolicyFindingsWhenSecurityEngineFoundNothing() {
        AnalysisRun analysisRun = analysisRunWithRepository("org/core");
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        stubApprovedVerdict(analysisRun);
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

    private void stubApprovedVerdict(AnalysisRun analysisRun) {
        VerdictResult verdictResult = new VerdictResult(ANALYSIS_RUN_ID, VerdictOutcome.APPROVED, List.of(), Instant.now());
        when(verdictEngine.evaluate(any())).thenReturn(verdictResult);
        Verdict savedVerdict = Verdict.builder().analysisRun(analysisRun).outcome(VerdictOutcome.APPROVED).build();
        when(verdictRepository.save(any())).thenReturn(savedVerdict);
    }

    private AnalysisRun analysisRunWithRepository(String repositoryFullName) {
        Repository repository = Repository.builder().fullName(repositoryFullName).build();
        PullRequest pullRequest = PullRequest.builder().repository(repository).number(7).build();
        AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).build();
        ReflectionTestUtils.setField(analysisRun, "id", ANALYSIS_RUN_ID);
        return analysisRun;
    }
}
