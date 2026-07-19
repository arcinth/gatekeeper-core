package com.gatekeeper.repository;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.repository.dto.RepositoryGovernanceResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A separate controller from RepositoryController, sharing its
 * {@code /api/v1/repositories} URL prefix but contributing only
 * {@code GET /{id}/governance} (Repository Governance View Architecture,
 * Section 7) - the same additive-sibling-controller pattern ReportController
 * already established under {@code /api/v1/analysis-runs} (ADR-048).
 * RepositoryController itself is untouched.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('WORKSPACE_READ')")
public class RepositoryGovernanceController {

    private final RepositoryGovernanceService repositoryGovernanceService;

    @GetMapping("/{id}/governance")
    public ApiResponse<RepositoryGovernanceResponse> getGovernanceSummary(
            @PathVariable Long id, @RequestParam(required = false) Integer windowDays) {
        return ApiResponse.ok(repositoryGovernanceService.getGovernanceSummary(id, windowDays));
    }
}
