package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.policy.PolicyResult;
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import com.gatekeeper.policyfinding.PolicyFindingMapper;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.securityengine.SecurityResult;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingMapper;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictMapper;
import com.gatekeeper.verdict.VerdictReasonEntity;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictEngine;
import com.gatekeeper.verdictengine.VerdictResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists both engines' findings, evaluates and persists the
 * Verdict, and marks the AnalysisRun COMPLETED - every write and the
 * terminal-state transition succeed together or none of them do (Security
 * Engine Architecture, Section 16; extended for Verdict in Sprint 5
 * Architecture, Section 10/17 - ADR-035).
 * <p>
 * Supersedes com.gatekeeper.policyfinding.PolicyFindingPersistenceService,
 * which is removed as of this milestone (ADR-025): "mark this run COMPLETED"
 * stopped being a Policy-specific concern the moment a second engine's
 * findings also had to land before that same transition. PolicyFindingEntity,
 * PolicyFindingMapper, and PolicyFindingRepository are unaffected - this
 * service simply calls PolicyFindingMapper.toEntity the same way
 * PolicyFindingPersistenceService did.
 * <p>
 * <b>Verdict evaluation (Sprint 5 Milestone 2).</b> Runs synchronously, in
 * this same transaction, immediately after both engines' findings are saved
 * and before {@code markCompleted} - not as a separate event-driven flow
 * like AI Review's. VerdictEngine has no I/O of its own (Sprint 5
 * Architecture, Section 6), so evaluating it here costs nothing in latency
 * and buys a database-enforced guarantee an async path could not: a
 * COMPLETED AnalysisRun always has exactly one Verdict (ADR-039), with no
 * observable window where it doesn't. If VerdictEngine.evaluate itself
 * throws (as opposed to one isolated VerdictRule failing internally), that
 * exception is deliberately not caught here - it propagates, rolling back
 * this entire transaction (Policy findings, Security findings, and the
 * COMPLETED transition all together), falling through to
 * AnalysisExecutionService's existing outer catch, which marks the
 * AnalysisRun FAILED. This is the opposite of AI Review's failure
 * philosophy, deliberately: AI Review is advisory and optional; a
 * governance platform silently completing a run without producing the one
 * thing it exists to produce would be a worse failure mode (Sprint 5
 * Architecture, Section 16 / ADR-038).
 * <p>
 * Deliberately a separate bean from AnalysisExecutionService, for the same
 * Spring AOP self-invocation reason PolicyFindingPersistenceService already
 * documented: AnalysisExecutionService's entry point runs through its own
 * {@code @Async} proxy, so a {@code @Transactional} method declared directly
 * on that class would be self-invoked and silently run without a transaction.
 * <p>
 * <b>VerdictProducedEvent (Unified Engineering Report Architecture,
 * Milestone 1).</b> Published at the very end of this same transaction,
 * after the Verdict is persisted and the run is marked COMPLETED - purely
 * additive, no existing behavior above it changes. ReportGenerationListener
 * is the only consumer; it swallows its own exceptions, so nothing about
 * report generation can affect this method, its transaction, or its caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisResultPersistenceService {

    private final AnalysisRunService analysisRunService;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final VerdictEngine verdictEngine;
    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

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

        VerdictResult verdictResult = evaluateVerdict(analysisRun, policyResult, securityResult);
        persistVerdict(analysisRun, verdictResult);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.VERDICT_PRODUCED)
                .organizationId(analysisRun.getPullRequest().getRepository().getOrganization().getId())
                .repositoryId(analysisRun.getPullRequest().getRepository().getId())
                .pullRequestId(analysisRun.getPullRequest().getId())
                .analysisRunId(analysisRunId)
                .newValue(Map.of("outcome", verdictResult.outcome().name()))
                .summary("Verdict " + verdictResult.outcome() + " produced for analysis run " + analysisRunId + ".")
                .build());

        analysisRunService.markCompleted(analysisRun);

        log.info("Persisted {} policy finding(s), {} security finding(s), and a {} verdict; "
                        + "marked analysis run {} COMPLETED.",
                policyEntities.size(), securityEntities.size(), verdictResult.outcome(), analysisRunId);

        eventPublisher.publishEvent(new VerdictProducedEvent(analysisRunId));
    }

    private VerdictResult evaluateVerdict(AnalysisRun analysisRun, PolicyResult policyResult, SecurityResult securityResult) {
        String repositoryFullName = analysisRun.getPullRequest().getRepository().getFullName();
        VerdictContext context = new VerdictContext(
                analysisRun.getId(), repositoryFullName, policyResult.findings(), securityResult.findings());
        return verdictEngine.evaluate(context);
    }

    private void persistVerdict(AnalysisRun analysisRun, VerdictResult verdictResult) {
        Verdict verdict = verdictRepository.save(VerdictMapper.toEntity(analysisRun, verdictResult));

        List<VerdictReasonEntity> reasonEntities = verdictResult.reasons().stream()
                .map(reason -> VerdictMapper.toReasonEntity(verdict, reason))
                .toList();
        verdictReasonRepository.saveAll(reasonEntities);
    }
}
