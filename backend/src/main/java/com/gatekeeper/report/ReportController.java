package com.gatekeeper.report;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.report.dto.ReportDetailResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A separate controller from AnalysisRunController, sharing its
 * {@code /api/v1/analysis-runs} URL prefix but contributing only
 * {@code GET /{id}/report} (Unified Engineering Report Architecture, Section
 * 9 / ADR-048) - the report is a sub-resource of AnalysisRun, not a
 * standalone paginated list, so it has no findAll and no filters, unlike
 * VerdictController/SecurityFindingController. AnalysisRunController itself
 * is untouched.
 */
@RestController
@RequestMapping("/api/v1/analysis-runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportQueryService reportQueryService;

    @GetMapping("/{id}/report")
    public ApiResponse<ReportDetailResponse> findByAnalysisRunId(@PathVariable Long id) {
        return ApiResponse.ok(reportQueryService.findByAnalysisRunIdOrThrow(id));
    }
}
