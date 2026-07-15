package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.policy.PolicyResult;
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import com.gatekeeper.policyfinding.PolicyFindingMapper;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.securityengine.SecurityResult;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingMapper;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists both engines' findings and marks the AnalysisRun
 * COMPLETED - both engines' writes and the terminal-state transition succeed
 * together or none of them do (Security Engine Architecture, Section 16).
 * <p>
 * Supersedes com.gatekeeper.policyfinding.PolicyFindingPersistenceService,
 * which is removed as of this milestone (ADR-025): "mark this run COMPLETED"
 * stopped being a Policy-specific concern the moment a second engine's
 * findings also had to land before that same transition. PolicyFindingEntity,
 * PolicyFindingMapper, and PolicyFindingRepository are unaffected - this
 * service simply calls PolicyFindingMapper.toEntity the same way
 * PolicyFindingPersistenceService did.
 * <p>
 * Deliberately a separate bean from AnalysisExecutionService, for the same
 * Spring AOP self-invocation reason PolicyFindingPersistenceService already
 * documented: AnalysisExecutionService's entry point runs through its own
 * {@code @Async} proxy, so a {@code @Transactional} method declared directly
 * on that class would be self-invoked and silently run without a transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisResultPersistenceService {

    private final AnalysisRunService analysisRunService;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;

    @Transactional
    public void persistCompletedResult(Long analysisRunId, PolicyResult policyResult, SecurityResult securityResult) {
        AnalysisRun analysisRun = analysisRunService.findByIdOrThrow(analysisRunId);

        List<PolicyFindingEntity> policyEntities = policyResult.findings().stream()
                .map(finding -> PolicyFindingMapper.toEntity(analysisRun, finding))
                .toList();
        policyFindingRepository.saveAll(policyEntities);

        List<SecurityFindingEntity> securityEntities = securityResult.findings().stream()
                .map(finding -> SecurityFindingMapper.toEntity(analysisRun, finding))
                .toList();
        securityFindingRepository.saveAll(securityEntities);

        analysisRunService.markCompleted(analysisRun);

        log.info("Persisted {} policy finding(s) and {} security finding(s); marked analysis run {} COMPLETED.",
                policyEntities.size(), securityEntities.size(), analysisRunId);
    }
}
