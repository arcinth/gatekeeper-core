package com.gatekeeper.orchestration;

import com.gatekeeper.aireviewengine.AIReviewProvider;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewfinding.AIReviewFindingEntity;
import com.gatekeeper.aireviewfinding.AIReviewFindingMapper;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the outcome of one AI Review Engine invocation - either a
 * completed run with its findings, or a failed run with a reason - as its
 * own AIReviewRun row. Never touches AnalysisRun's own status (contrast
 * AnalysisResultPersistenceService, which marks AnalysisRun COMPLETED as
 * part of the same atomic write): AI review's lifecycle is independent by
 * design (Sprint 4 Milestone 3; see AIReviewRun's Javadoc).
 * <p>
 * Deliberately a separate bean from AIReviewExecutionService, for the same
 * Spring AOP self-invocation reason AnalysisResultPersistenceService already
 * documented: AIReviewExecutionService's entry point runs through its own
 * {@code @Async} proxy, so a {@code @Transactional} method declared directly
 * on that class would be self-invoked and silently run without a transaction.
 * <p>
 * Reads provider/model/promptVersion from the injected AIReviewProvider bean
 * itself, not from AIReviewResult, so the same identity metadata is available
 * uniformly on both the completed and failed paths - a failed review never
 * produces an AIReviewResult to read those fields from, but the provider's
 * own identity is static, configuration-derived, and always available
 * regardless of whether the call succeeded (see AIReviewProvider#modelName's
 * Javadoc).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIReviewResultPersistenceService {

    private static final int FAILURE_REASON_MAX_LENGTH = 2000;

    private final AnalysisRunService analysisRunService;
    private final AIReviewProvider aiReviewProvider;
    private final AIReviewRunRepository aiReviewRunRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;

    @Transactional
    public void persistCompletedResult(Long analysisRunId, AIReviewResult result) {
        AnalysisRun analysisRun = analysisRunService.findByIdOrThrow(analysisRunId);

        AIReviewRun aiReviewRun = aiReviewRunRepository.save(AIReviewRun.builder()
                .analysisRun(analysisRun)
                .status(AIReviewRunStatus.COMPLETED)
                .provider(aiReviewProvider.providerName())
                .model(aiReviewProvider.modelName())
                .promptVersion(aiReviewProvider.promptVersion())
                .summary(result.summary())
                .build());

        List<AIReviewFindingEntity> findingEntities = result.findings().stream()
                .map(finding -> AIReviewFindingMapper.toEntity(aiReviewRun, finding))
                .toList();
        aiReviewFindingRepository.saveAll(findingEntities);

        log.info("Persisted AI review run {} for analysis run {}: {} finding(s) from provider '{}'.",
                aiReviewRun.getId(), analysisRunId, findingEntities.size(), aiReviewProvider.providerName());
    }

    @Transactional
    public void persistFailedResult(Long analysisRunId, String failureReason) {
        AnalysisRun analysisRun = analysisRunService.findByIdOrThrow(analysisRunId);

        AIReviewRun aiReviewRun = aiReviewRunRepository.save(AIReviewRun.builder()
                .analysisRun(analysisRun)
                .status(AIReviewRunStatus.FAILED)
                .provider(aiReviewProvider.providerName())
                .model(aiReviewProvider.modelName())
                .promptVersion(aiReviewProvider.promptVersion())
                .failureReason(truncate(failureReason))
                .build());

        log.warn("Recorded failed AI review run {} for analysis run {}: {}",
                aiReviewRun.getId(), analysisRunId, failureReason);
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > FAILURE_REASON_MAX_LENGTH ? reason.substring(0, FAILURE_REASON_MAX_LENGTH) : reason;
    }
}
