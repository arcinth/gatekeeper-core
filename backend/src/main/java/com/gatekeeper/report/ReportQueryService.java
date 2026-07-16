package com.gatekeeper.report;

import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingResponse;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.auditlog.AuditLogRepository;
import com.gatekeeper.auditlog.dto.AuditLogResponse;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.policyfinding.dto.PolicyFindingResponse;
import com.gatekeeper.report.dto.ReportDetailResponse;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.securityfinding.dto.SecurityFindingResponse;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdict.dto.VerdictReasonSummary;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for EngineeringReport. {@link #findByAnalysisRunId} is the core
 * lookup capability from Milestone 1; {@link #findByAnalysisRunIdOrThrow} is
 * Milestone 2's full Report Detail composition across Policy/Security/AI
 * findings, Verdict, and Audit Log (Unified Engineering Report Architecture,
 * Section 9) - the REST layer this milestone builds on top of it. Mirrors
 * VerdictQueryService's separation from its write side
 * (AnalysisResultPersistenceService/ReportPublicationService own writes;
 * this class is query-only, and never touches report publication logic).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {

    private final EngineeringReportRepository engineeringReportRepository;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AIReviewRunRepository aiReviewRunRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;
    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;
    private final AuditLogRepository auditLogRepository;

    public Optional<EngineeringReport> findByAnalysisRunId(Long analysisRunId) {
        return engineeringReportRepository.findByAnalysisRunId(analysisRunId);
    }

    /**
     * Composes the full Unified Engineering Report for one AnalysisRun.
     * 404s uniformly whether no report has been published yet (still
     * pending, e.g. waiting on AI review) or will never exist (the run
     * itself never reached a Verdict) - the client doesn't need to
     * distinguish these, and neither exposes ReportPublicationService's
     * internal join state (Section 9).
     */
    public ReportDetailResponse findByAnalysisRunIdOrThrow(Long analysisRunId) {
        EngineeringReport report = engineeringReportRepository.findWithContextByAnalysisRunId(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Engineering report not found for analysis run: " + analysisRunId));

        List<PolicyFindingResponse> policyFindings = policyFindingRepository
                .findByAnalysisRunIdOrderById(analysisRunId).stream()
                .map(PolicyFindingResponse::from)
                .toList();
        List<SecurityFindingResponse> securityFindings = securityFindingRepository
                .findByAnalysisRunIdOrderById(analysisRunId).stream()
                .map(SecurityFindingResponse::from)
                .toList();
        List<AIReviewFindingResponse> aiFindings = aiFindingsFor(analysisRunId, report.getAiReviewStatus());

        Verdict verdict = verdictRepository.findByAnalysisRunId(analysisRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "Engineering report " + report.getId() + " exists for analysis run " + analysisRunId
                                + " but its Verdict does not - this violates the publication invariant "
                                + "(a report is never published before its run's Verdict exists)."));
        List<VerdictReasonSummary> verdictReasons = verdictReasonRepository
                .findByVerdictIdOrderById(verdict.getId()).stream()
                .map(VerdictReasonSummary::from)
                .toList();

        List<AuditLogResponse> auditTrail = auditLogRepository
                .findByAnalysisRunIdOrderByOccurredAt(analysisRunId).stream()
                .map(AuditLogResponse::from)
                .toList();

        return ReportDetailResponse.from(
                report, policyFindings, securityFindings, aiFindings, verdict.getOutcome(), verdictReasons, auditTrail);
    }

    /**
     * Empty for UNAVAILABLE/DISABLED regardless of whether an AIReviewRun
     * technically exists (e.g. FAILED) - aiReviewStatus, recorded once at
     * publication time, is the authoritative signal for what belongs in this
     * report, not a fresh re-derivation from AIReviewRun's current state
     * (ADR-050).
     */
    private List<AIReviewFindingResponse> aiFindingsFor(Long analysisRunId, AiReviewStatus aiReviewStatus) {
        if (aiReviewStatus != AiReviewStatus.INCLUDED) {
            return List.of();
        }
        return aiReviewRunRepository.findByAnalysisRunId(analysisRunId)
                .map(run -> aiReviewFindingRepository.findByAiReviewRunIdOrderById(run.getId()).stream()
                        .map(AIReviewFindingResponse::from)
                        .toList())
                .orElse(List.of());
    }
}
