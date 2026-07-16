package com.gatekeeper.report;

import com.gatekeeper.analysisrun.AnalysisRun;

/**
 * Converts an AnalysisRun and its resolved {@link AiReviewStatus} into a
 * persistable EngineeringReport. Mirrors VerdictMapper's shape: a single,
 * public (called from com.gatekeeper.orchestration - ADR-025 precedent
 * extended a fourth time), unsaved-entity-returning static method, since
 * there is no intermediate "result" record to map from - report generation
 * has no computation step of its own, only the aiReviewStatus already
 * resolved by ReportPublicationService's join logic (Section 6).
 */
public final class EngineeringReportMapper {

    private EngineeringReportMapper() {
    }

    public static EngineeringReport toEntity(AnalysisRun analysisRun, AiReviewStatus aiReviewStatus) {
        return EngineeringReport.builder()
                .analysisRun(analysisRun)
                .aiReviewStatus(aiReviewStatus)
                .build();
    }
}
