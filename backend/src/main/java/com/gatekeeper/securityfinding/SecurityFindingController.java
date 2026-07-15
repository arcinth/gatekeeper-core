package com.gatekeeper.securityfinding;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.dto.SecurityFindingFilter;
import com.gatekeeper.securityfinding.dto.SecurityFindingResponse;
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

/** Mirrors com.gatekeeper.policyfinding.PolicyFindingController exactly (Security Engine Architecture, Section 13). */
@RestController
@RequestMapping("/api/v1/security-findings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SecurityFindingController {

    private final SecurityFindingQueryService securityFindingQueryService;

    @GetMapping
    public ApiResponse<PageResponse<SecurityFindingResponse>> findAll(
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) SecuritySeverity severity,
            @RequestParam(required = false) SecurityCategory category,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        SecurityFindingFilter filter =
                new SecurityFindingFilter(analysisRunId, repositoryId, severity, category, ruleId, from, to);
        return ApiResponse.ok(PageResponse.from(securityFindingQueryService.findPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SecurityFindingResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(securityFindingQueryService.findByIdOrThrow(id));
    }
}
