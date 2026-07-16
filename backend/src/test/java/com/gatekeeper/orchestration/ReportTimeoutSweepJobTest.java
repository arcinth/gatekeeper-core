package com.gatekeeper.orchestration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.report.EngineeringReportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTimeoutSweepJobTest {

    private static final long TIMEOUT_SECONDS = 120L;

    private final EngineeringReportRepository engineeringReportRepository = mock(EngineeringReportRepository.class);
    private final ReportPublicationService reportPublicationService = mock(ReportPublicationService.class);

    private ReportTimeoutSweepJob job(boolean aiReviewEnabled) {
        return new ReportTimeoutSweepJob(aiReviewEnabled, TIMEOUT_SECONDS, engineeringReportRepository, reportPublicationService);
    }

    @Test
    void sweep_doesNothingWhenAiReviewIsDisabled() {
        job(false).sweep();

        verify(engineeringReportRepository, never()).findAnalysisRunIdsMissingReportPublishedBefore(any());
        verify(reportPublicationService, never()).publishOverdue(any());
    }

    @Test
    void sweep_doesNothingWhenNoRowsAreOverdue() {
        when(engineeringReportRepository.findAnalysisRunIdsMissingReportPublishedBefore(any())).thenReturn(List.of());

        job(true).sweep();

        verify(reportPublicationService, never()).publishOverdue(any());
    }

    @Test
    void sweep_forcePublishesEachOverdueAnalysisRun() {
        when(engineeringReportRepository.findAnalysisRunIdsMissingReportPublishedBefore(any()))
                .thenReturn(List.of(1L, 2L, 3L));

        job(true).sweep();

        verify(reportPublicationService).publishOverdue(1L);
        verify(reportPublicationService).publishOverdue(2L);
        verify(reportPublicationService).publishOverdue(3L);
    }

    /** Per-row fault isolation, mirroring VerdictEngine's own rule-level isolation. */
    @Test
    void sweep_continuesProcessingRemainingRowsWhenOnePublicationFails() {
        when(engineeringReportRepository.findAnalysisRunIdsMissingReportPublishedBefore(any()))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("simulated db error")).when(reportPublicationService).publishOverdue(2L);

        job(true).sweep();

        verify(reportPublicationService).publishOverdue(1L);
        verify(reportPublicationService).publishOverdue(2L);
        verify(reportPublicationService).publishOverdue(3L);
    }
}
