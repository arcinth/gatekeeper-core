package com.gatekeeper.orchestration;

import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLog;
import com.gatekeeper.auditlog.AuditLogRepository;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReport;
import com.gatekeeper.report.EngineeringReportMapper;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.verdict.VerdictRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single write that publishes a Unified Engineering Report -
 * exactly one EngineeringReport row (plus its paired AuditLog entry) per
 * AnalysisRun, ever (Unified Engineering Report Architecture, Section 6/12).
 * <p>
 * Report generation is a two-source event join, not a step chained onto
 * either the deterministic pipeline or AI review: a report can only be
 * published once both sides have reached a resolvable state, and there is no
 * ordering guarantee between them (ADR-045). This class exposes one entry
 * point per trigger side ({@link #onVerdictProduced} / {@link #onAiReviewFinished}),
 * each deciding independently whether the *other* side is already resolved
 * before publishing, plus {@link #publishOverdue} for
 * ReportTimeoutSweepJob's fallback path. All three converge on the same
 * idempotent {@link #publishIfAbsent}, protected by engineering_reports'
 * {@code UNIQUE(analysis_run_id)} constraint - if two callers race, the
 * loser's insert fails that constraint and is caught here as a no-op, the
 * same idempotency shape AnalysisRunService.createIfAbsent already
 * established.
 * <p>
 * Each public method is independently {@code @Transactional}: this is the
 * bean ReportGenerationListener's thin, non-transactional listener methods
 * delegate to (a different bean, invoked externally through its own proxy) -
 * the same self-invocation-avoidance split AnalysisExecutionService /
 * AnalysisResultPersistenceService and AIReviewExecutionService /
 * AIReviewResultPersistenceService already established. publishIfAbsent
 * itself is deliberately NOT separately {@code @Transactional}: it always
 * runs inside whichever caller's transaction is already active, exactly the
 * same private-helper-inside-a-transactional-method shape
 * AnalysisRunService's own countsByAnalysisRunId/verdictOutcomesByAnalysisRunId
 * already use.
 * <p>
 * A report-generation failure here must never affect the Verdict, the
 * AnalysisRun, or the AIReviewRun that already committed (ADR-047) - this
 * class never touches any of their tables, and its only caller
 * (ReportGenerationListener) swallows every exception this class throws.
 */
@Slf4j
@Service
public class ReportPublicationService {

    private final boolean aiReviewEnabled;
    private final AnalysisRunService analysisRunService;
    private final VerdictRepository verdictRepository;
    private final AIReviewRunRepository aiReviewRunRepository;
    private final EngineeringReportRepository engineeringReportRepository;
    private final AuditLogRepository auditLogRepository;

    public ReportPublicationService(
            @Value("${gatekeeper.ai-review.enabled}") boolean aiReviewEnabled,
            AnalysisRunService analysisRunService,
            VerdictRepository verdictRepository,
            AIReviewRunRepository aiReviewRunRepository,
            EngineeringReportRepository engineeringReportRepository,
            AuditLogRepository auditLogRepository) {
        this.aiReviewEnabled = aiReviewEnabled;
        this.analysisRunService = analysisRunService;
        this.verdictRepository = verdictRepository;
        this.aiReviewRunRepository = aiReviewRunRepository;
        this.engineeringReportRepository = engineeringReportRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * If AI review is disabled entirely, no AIReviewRun will ever exist for
     * this run, so publishing must not wait for one (Section 6, step 1).
     * Otherwise, publish now only if AI review already reached a terminal
     * state; if not, do nothing - AIReviewFinishedEvent's own handler will
     * trigger publication later, or the timeout sweep eventually will.
     */
    @Transactional
    public void onVerdictProduced(Long analysisRunId) {
        if (!aiReviewEnabled) {
            publishIfAbsent(analysisRunId, AiReviewStatus.DISABLED);
            return;
        }
        aiReviewRunRepository.findByAnalysisRunId(analysisRunId)
                .ifPresent(run -> publishIfAbsent(analysisRunId, toAiReviewStatus(run.getStatus())));
    }

    /** Publish now only if the deterministic pipeline already produced a Verdict; otherwise wait for VerdictProducedEvent. */
    @Transactional
    public void onAiReviewFinished(Long analysisRunId, AIReviewRunStatus status) {
        if (verdictRepository.findByAnalysisRunId(analysisRunId).isPresent()) {
            publishIfAbsent(analysisRunId, toAiReviewStatus(status));
        }
    }

    /**
     * ReportTimeoutSweepJob has already restricted its candidates to
     * AnalysisRuns whose Verdict is older than the configured grace window
     * and still has no report, so this force-publishes unconditionally
     * (ADR-046) - the wait is over.
     */
    @Transactional
    public void publishOverdue(Long analysisRunId) {
        publishIfAbsent(analysisRunId, AiReviewStatus.UNAVAILABLE);
    }

    private void publishIfAbsent(Long analysisRunId, AiReviewStatus aiReviewStatus) {
        if (engineeringReportRepository.existsByAnalysisRunId(analysisRunId)) {
            return;
        }
        try {
            AnalysisRun analysisRun = analysisRunService.findByIdOrThrow(analysisRunId);
            EngineeringReport report = engineeringReportRepository.save(
                    EngineeringReportMapper.toEntity(analysisRun, aiReviewStatus));
            auditLogRepository.save(AuditLog.builder()
                    .organization(organizationOf(analysisRun))
                    .analysisRun(analysisRun)
                    .eventType(AuditEventType.ENGINEERING_REPORT_PUBLISHED)
                    .summary("Engineering report published for analysis run " + analysisRunId + ".")
                    .build());
            log.info("Published engineering report {} for analysis run {} (aiReviewStatus={}).",
                    report.getId(), analysisRunId, aiReviewStatus);
        } catch (DataIntegrityViolationException ex) {
            log.info("Engineering report for analysis run {} was already published concurrently; skipping.",
                    analysisRunId);
        }
    }

    private AiReviewStatus toAiReviewStatus(AIReviewRunStatus status) {
        return status == AIReviewRunStatus.COMPLETED ? AiReviewStatus.INCLUDED : AiReviewStatus.UNAVAILABLE;
    }

    private Organization organizationOf(AnalysisRun analysisRun) {
        return analysisRun.getPullRequest().getRepository().getOrganization();
    }
}
