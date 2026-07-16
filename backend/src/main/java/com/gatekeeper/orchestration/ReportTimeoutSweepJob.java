package com.gatekeeper.orchestration;

import com.gatekeeper.report.EngineeringReportRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded fallback for the report-generation join (Unified Engineering
 * Report Architecture, Section 6 / ADR-046): if AI review's async task never
 * even starts (e.g. its executor queue rejects it before any AIReviewRun row
 * is written), neither VerdictProducedEvent's nor AIReviewFinishedEvent's
 * handler ever finds "the other side ready," and a report would otherwise
 * wait forever. This sweep periodically force-publishes any AnalysisRun
 * whose Verdict is older than the configured grace window and still has no
 * EngineeringReport, with {@code aiReviewStatus = UNAVAILABLE} - extending
 * "AI Review must never delay a governance decision" to "must never delay
 * the report either."
 * <p>
 * First use of {@code @Scheduled} in this codebase (see GateKeeperApplication's
 * {@code @EnableScheduling}) - a genuinely new piece of infrastructure, not
 * an extension of an existing pattern.
 * <p>
 * Skips entirely when AI review is disabled: ReportPublicationService.onVerdictProduced
 * already publishes immediately in that case (Section 6, step 1), so nothing
 * can be stuck waiting on a signal that will never arrive, and there is
 * nothing for this sweep to find.
 * <p>
 * Each row is published via ReportPublicationService.publishOverdue - a
 * separate {@code @Transactional} call per row, so one row's failure is
 * caught and logged without preventing the remaining rows in the same sweep
 * from being processed (the same per-item fault isolation VerdictEngine
 * already established for its rules).
 */
@Slf4j
@Component
public class ReportTimeoutSweepJob {

    private final boolean aiReviewEnabled;
    private final long aiWaitTimeoutSeconds;
    private final EngineeringReportRepository engineeringReportRepository;
    private final ReportPublicationService reportPublicationService;

    public ReportTimeoutSweepJob(
            @Value("${gatekeeper.ai-review.enabled}") boolean aiReviewEnabled,
            @Value("${gatekeeper.report.ai-wait-timeout-seconds}") long aiWaitTimeoutSeconds,
            EngineeringReportRepository engineeringReportRepository,
            ReportPublicationService reportPublicationService) {
        this.aiReviewEnabled = aiReviewEnabled;
        this.aiWaitTimeoutSeconds = aiWaitTimeoutSeconds;
        this.engineeringReportRepository = engineeringReportRepository;
        this.reportPublicationService = reportPublicationService;
    }

    @Scheduled(fixedDelayString = "${gatekeeper.report.sweep-interval-ms}")
    public void sweep() {
        if (!aiReviewEnabled) {
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(aiWaitTimeoutSeconds);
        List<Long> overdueAnalysisRunIds = engineeringReportRepository.findAnalysisRunIdsMissingReportPublishedBefore(cutoff);
        for (Long analysisRunId : overdueAnalysisRunIds) {
            try {
                reportPublicationService.publishOverdue(analysisRunId);
            } catch (RuntimeException ex) {
                log.error("Failed to force-publish overdue engineering report for analysis run {}; "
                        + "will retry on the next sweep.", analysisRunId, ex);
            }
        }

        if (!overdueAnalysisRunIds.isEmpty()) {
            log.info("Force-published {} overdue engineering report(s) after a {}s AI review wait timeout.",
                    overdueAnalysisRunIds.size(), aiWaitTimeoutSeconds);
        }
    }
}
