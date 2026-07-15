package com.gatekeeper.dashboard;

import com.gatekeeper.common.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET / is a Sprint 1 placeholder (docs/API-Design.md GET /api/v1/dashboard).
 * GET /overview is the aggregate endpoint that placeholder's Javadoc deferred
 * until Analysis Runs and Findings existed to aggregate - built in Milestone 5.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private static final String VERSION = "1.0.0";

    private final DashboardAggregationService dashboardAggregationService;

    @GetMapping
    public ApiResponse<DashboardStatusResponse> status() {
        return ApiResponse.ok(new DashboardStatusResponse("running", VERSION));
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview(@RequestParam(required = false) Integer windowDays) {
        return ApiResponse.ok(dashboardAggregationService.getOverview(windowDays));
    }
}
