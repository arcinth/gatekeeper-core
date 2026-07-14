package com.gatekeeper.dashboard;

import com.gatekeeper.common.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 1 placeholder only (docs/API-Design.md GET /api/v1/dashboard).
 * The aggregated /dashboard/overview and /dashboard/statistics endpoints are deferred
 * until Analysis Runs, Findings, and Verdicts exist to aggregate in a later sprint.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private static final String VERSION = "1.0.0";

    @GetMapping
    public ApiResponse<DashboardStatusResponse> status() {
        return ApiResponse.ok(new DashboardStatusResponse("running", VERSION));
    }
}
