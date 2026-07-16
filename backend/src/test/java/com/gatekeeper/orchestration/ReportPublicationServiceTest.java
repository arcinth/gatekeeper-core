package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.auditlog.AuditLog;
import com.gatekeeper.auditlog.AuditLogRepository;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReport;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPublicationServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final AIReviewRunRepository aiReviewRunRepository = mock(AIReviewRunRepository.class);
    private final EngineeringReportRepository engineeringReportRepository = mock(EngineeringReportRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private ReportPublicationService service(boolean aiReviewEnabled) {
        return new ReportPublicationService(aiReviewEnabled, analysisRunService, verdictRepository,
                aiReviewRunRepository, engineeringReportRepository, auditLogRepository);
    }

    private void stubSaveReturnsSavedEntityWithAnId() {
        when(engineeringReportRepository.save(any())).thenAnswer(invocation -> {
            EngineeringReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 99L);
            return report;
        });
    }

    // --- onVerdictProduced --------------------------------------------------

    @Test
    void onVerdictProduced_publishesImmediatelyWithDisabledStatusWhenAiReviewIsDisabled() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        stubSaveReturnsSavedEntityWithAnId();

        service(false).onVerdictProduced(ANALYSIS_RUN_ID);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.DISABLED);
        assertThat(reportCaptor.getValue().getAnalysisRun()).isSameAs(analysisRun);
        verify(aiReviewRunRepository, never()).findByAnalysisRunId(any());
    }

    @Test
    void onVerdictProduced_doesNothingWhenAiReviewIsEnabledAndNoTerminalRunExistsYet() {
        when(aiReviewRunRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.empty());

        service(true).onVerdictProduced(ANALYSIS_RUN_ID);

        verify(engineeringReportRepository, never()).existsByAnalysisRunId(any());
        verify(engineeringReportRepository, never()).save(any());
    }

    @Test
    void onVerdictProduced_publishesWithIncludedStatusWhenACompletedAiReviewRunAlreadyExists() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        AIReviewRun completedRun = AIReviewRun.builder().status(AIReviewRunStatus.COMPLETED).build();
        when(aiReviewRunRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(completedRun));
        stubSaveReturnsSavedEntityWithAnId();

        service(true).onVerdictProduced(ANALYSIS_RUN_ID);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.INCLUDED);
    }

    @Test
    void onVerdictProduced_publishesWithUnavailableStatusWhenAFailedAiReviewRunAlreadyExists() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        AIReviewRun failedRun = AIReviewRun.builder().status(AIReviewRunStatus.FAILED).build();
        when(aiReviewRunRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(failedRun));
        stubSaveReturnsSavedEntityWithAnId();

        service(true).onVerdictProduced(ANALYSIS_RUN_ID);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.UNAVAILABLE);
    }

    @Test
    void onVerdictProduced_doesNotPublishAgainWhenAReportAlreadyExists() {
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(true);

        service(false).onVerdictProduced(ANALYSIS_RUN_ID);

        verify(engineeringReportRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
        verify(analysisRunService, never()).findByIdOrThrow(any());
    }

    // --- onAiReviewFinished --------------------------------------------------

    @Test
    void onAiReviewFinished_publishesWithIncludedStatusWhenAVerdictAlreadyExists() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID))
                .thenReturn(Optional.of(Verdict.builder().outcome(VerdictOutcome.APPROVED).build()));
        stubSaveReturnsSavedEntityWithAnId();

        service(true).onAiReviewFinished(ANALYSIS_RUN_ID, AIReviewRunStatus.COMPLETED);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.INCLUDED);
    }

    @Test
    void onAiReviewFinished_publishesWithUnavailableStatusWhenTheAiReviewItselfFailed() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID))
                .thenReturn(Optional.of(Verdict.builder().outcome(VerdictOutcome.BLOCKED).build()));
        stubSaveReturnsSavedEntityWithAnId();

        service(true).onAiReviewFinished(ANALYSIS_RUN_ID, AIReviewRunStatus.FAILED);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.UNAVAILABLE);
    }

    @Test
    void onAiReviewFinished_doesNothingWhenNoVerdictExistsYet() {
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.empty());

        service(true).onAiReviewFinished(ANALYSIS_RUN_ID, AIReviewRunStatus.COMPLETED);

        verify(engineeringReportRepository, never()).existsByAnalysisRunId(any());
        verify(engineeringReportRepository, never()).save(any());
    }

    // --- publishOverdue --------------------------------------------------

    @Test
    void publishOverdue_forcePublishesWithUnavailableStatusUnconditionally() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        stubSaveReturnsSavedEntityWithAnId();

        service(true).publishOverdue(ANALYSIS_RUN_ID);

        ArgumentCaptor<EngineeringReport> reportCaptor = ArgumentCaptor.forClass(EngineeringReport.class);
        verify(engineeringReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getAiReviewStatus()).isEqualTo(AiReviewStatus.UNAVAILABLE);
    }

    // --- publication side effects / idempotency --------------------------------------------------

    @Test
    void publish_writesAPairedAuditLogEntryInTheSamePublishCall() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        stubSaveReturnsSavedEntityWithAnId();

        service(false).onVerdictProduced(ANALYSIS_RUN_ID);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAnalysisRun()).isSameAs(analysisRun);
        assertThat(auditCaptor.getValue().getOrganization()).isSameAs(analysisRun.getPullRequest().getRepository().getOrganization());
    }

    @Test
    void publish_underAConcurrentRace_swallowsTheUniqueConstraintViolationAndNeverWritesTheAuditEntry() {
        AnalysisRun analysisRun = analysisRunWithOrganization();
        when(analysisRunService.findByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(analysisRun);
        when(engineeringReportRepository.existsByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(false);
        when(engineeringReportRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_engineering_reports_analysis_run_id"));

        service(false).onVerdictProduced(ANALYSIS_RUN_ID);

        // The other, concurrent trigger already won the race - this call must not
        // throw, and must not write a second (orphaned) audit entry for a report
        // this call never actually created.
        verify(auditLogRepository, never()).save(any());
    }

    private AnalysisRun analysisRunWithOrganization() {
        Organization organization = Organization.builder().name("acme").build();
        Repository repository = Repository.builder().fullName("org/core").organization(organization).build();
        PullRequest pullRequest = PullRequest.builder().repository(repository).number(7).build();
        AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).build();
        ReflectionTestUtils.setField(analysisRun, "id", ANALYSIS_RUN_ID);
        return analysisRun;
    }
}
