package com.gatekeeper.policyfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.policy.PolicyResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists a PolicyResult's findings and marks the AnalysisRun
 * COMPLETED - both succeed or neither does (Milestone 4 Architecture,
 * Section 6). Deliberately a separate bean from AnalysisExecutionService
 * rather than a @Transactional method on it: AnalysisExecutionService's
 * entry point is itself called via its own @Async proxy, and a
 * @Transactional method on that same class would be self-invoked internally
 * and silently run with no transaction at all (a classic Spring AOP proxy
 * gotcha). Being a distinct bean means this method is always called from
 * outside its own class, so the @Transactional proxy is guaranteed to apply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyFindingPersistenceService {

    private final AnalysisRunService analysisRunService;
    private final PolicyFindingRepository policyFindingRepository;

    @Transactional
    public void persistCompletedResult(Long analysisRunId, PolicyResult result) {
        AnalysisRun analysisRun = analysisRunService.findByIdOrThrow(analysisRunId);

        List<PolicyFindingEntity> entities = result.findings().stream()
                .map(finding -> PolicyFindingMapper.toEntity(analysisRun, finding))
                .toList();
        policyFindingRepository.saveAll(entities);

        analysisRunService.markCompleted(analysisRun);

        log.info("Persisted {} policy finding(s) and marked analysis run {} COMPLETED.",
                entities.size(), analysisRunId);
    }
}
