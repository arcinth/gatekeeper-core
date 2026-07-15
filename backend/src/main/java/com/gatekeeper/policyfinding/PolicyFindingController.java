package com.gatekeeper.policyfinding;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.dto.PolicyFindingFilter;
import com.gatekeeper.policyfinding.dto.PolicyFindingResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy-findings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PolicyFindingController {

    private final PolicyFindingQueryService policyFindingQueryService;

    @GetMapping
    public ApiResponse<PageResponse<PolicyFindingResponse>> findAll(
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) PolicySeverity severity,
            @RequestParam(required = false) PolicyCategory category,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        PolicyFindingFilter filter = new PolicyFindingFilter(analysisRunId, repositoryId, severity, category, ruleId, from, to);
        return ApiResponse.ok(PageResponse.from(policyFindingQueryService.findPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PolicyFindingResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(policyFindingQueryService.findByIdOrThrow(id));
    }
}
