package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFinding;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewengine.AIReviewProvider;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewfinding.AIReviewFindingEntity;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AIReviewResultPersistenceServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final AIReviewProvider aiReviewProvider = mock(AIReviewProvider.class);
    private final AIReviewRunRepository aiReviewRunRepository = mock(AIReviewRunRepository.class);
    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AIReviewResultPersistenceService service = new AIReviewResultPersistenceService(
            analysisRunService, aiReviewProvider, aiReviewRunRepository, aiReviewFindingRepository, eventPublisher);

    @Test
    void persistCompletedResult_savesAnAiReviewRunStampedWithProviderModelAndPromptVersion() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(aiReviewProvider.providerName()).thenReturn("anthropic-claude");
        when(aiReviewProvider.modelName()).thenReturn("claude-opus-4-6");
        when(aiReviewProvider.promptVersion()).thenReturn("v1");
        when(aiReviewRunRepository.save(org.mockito.ArgumentMatchers.any(AIReviewRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIReviewFinding finding = new AIReviewFinding(
                AIReviewFindingType.POTENTIAL_BUG, AIReviewConfidence.HIGH, "a.java", 1, "msg", "rec");
        AIReviewResult result = new AIReviewResult(
                ANALYSIS_RUN_ID, "anthropic-claude", "summary text", List.of(finding), Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, result);

        ArgumentCaptor<AIReviewRun> runCaptor = ArgumentCaptor.forClass(AIReviewRun.class);
        verify(aiReviewRunRepository).save(runCaptor.capture());
        AIReviewRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getAnalysisRun()).isSameAs(analysisRun);
        assertThat(savedRun.getStatus()).isEqualTo(AIReviewRunStatus.COMPLETED);
        assertThat(savedRun.getProvider()).isEqualTo("anthropic-claude");
        assertThat(savedRun.getModel()).isEqualTo("claude-opus-4-6");
        assertThat(savedRun.getPromptVersion()).isEqualTo("v1");
        assertThat(savedRun.getSummary()).isEqualTo("summary text");
        assertThat(savedRun.getFailureReason()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AIReviewFindingEntity>> findingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiReviewFindingRepository).saveAll(findingsCaptor.capture());
        assertThat(findingsCaptor.getValue()).hasSize(1);
        assertThat(findingsCaptor.getValue().get(0).getAiReviewRun()).isSameAs(savedRun);

        ArgumentCaptor<AIReviewFinishedEvent> eventCaptor = ArgumentCaptor.forClass(AIReviewFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().analysisRunId()).isEqualTo(ANALYSIS_RUN_ID);
        assertThat(eventCaptor.getValue().status()).isEqualTo(AIReviewRunStatus.COMPLETED);
    }

    @Test
    void persistCompletedResult_persistsAnEmptyFindingsListWithoutError() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(aiReviewProvider.providerName()).thenReturn("anthropic-claude");
        when(aiReviewProvider.modelName()).thenReturn("claude-opus-4-6");
        when(aiReviewProvider.promptVersion()).thenReturn("v1");
        when(aiReviewRunRepository.save(org.mockito.ArgumentMatchers.any(AIReviewRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIReviewResult cleanResult = new AIReviewResult(
                ANALYSIS_RUN_ID, "anthropic-claude", "all good", List.of(), Instant.now());

        service.persistCompletedResult(ANALYSIS_RUN_ID, cleanResult);

        verify(aiReviewFindingRepository).saveAll(anyList());
    }

    @Test
    void persistFailedResult_savesAFailedAiReviewRunWithTheReasonAndNoFindingsWrite() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(aiReviewProvider.providerName()).thenReturn("anthropic-claude");
        when(aiReviewProvider.modelName()).thenReturn("claude-opus-4-6");
        when(aiReviewProvider.promptVersion()).thenReturn("v1");
        when(aiReviewRunRepository.save(org.mockito.ArgumentMatchers.any(AIReviewRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.persistFailedResult(ANALYSIS_RUN_ID, "AI_PROVIDER_ERROR: timeout");

        ArgumentCaptor<AIReviewRun> runCaptor = ArgumentCaptor.forClass(AIReviewRun.class);
        verify(aiReviewRunRepository).save(runCaptor.capture());
        AIReviewRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getStatus()).isEqualTo(AIReviewRunStatus.FAILED);
        assertThat(savedRun.getFailureReason()).isEqualTo("AI_PROVIDER_ERROR: timeout");
        assertThat(savedRun.getSummary()).isNull();
        assertThat(savedRun.getProvider()).isEqualTo("anthropic-claude");
        assertThat(savedRun.getModel()).isEqualTo("claude-opus-4-6");
        assertThat(savedRun.getPromptVersion()).isEqualTo("v1");

        verify(aiReviewFindingRepository, org.mockito.Mockito.never()).saveAll(anyList());

        ArgumentCaptor<AIReviewFinishedEvent> eventCaptor = ArgumentCaptor.forClass(AIReviewFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().analysisRunId()).isEqualTo(ANALYSIS_RUN_ID);
        assertThat(eventCaptor.getValue().status()).isEqualTo(AIReviewRunStatus.FAILED);
    }

    @Test
    void persistFailedResult_truncatesAnOverlyLongFailureReason() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(aiReviewProvider.providerName()).thenReturn("anthropic-claude");
        when(aiReviewProvider.modelName()).thenReturn("claude-opus-4-6");
        when(aiReviewProvider.promptVersion()).thenReturn("v1");
        when(aiReviewRunRepository.save(org.mockito.ArgumentMatchers.any(AIReviewRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String longReason = "x".repeat(2500);

        service.persistFailedResult(ANALYSIS_RUN_ID, longReason);

        ArgumentCaptor<AIReviewRun> runCaptor = ArgumentCaptor.forClass(AIReviewRun.class);
        verify(aiReviewRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getFailureReason()).hasSize(2000);
    }
}
