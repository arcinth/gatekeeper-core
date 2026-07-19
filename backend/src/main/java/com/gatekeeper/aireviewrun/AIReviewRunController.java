package com.gatekeeper.aireviewrun;

import com.gatekeeper.aireviewrun.dto.AIReviewRunDetailResponse;
import com.gatekeeper.aireviewrun.dto.AIReviewRunFilter;
import com.gatekeeper.aireviewrun.dto.AIReviewRunSummaryResponse;
import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors com.gatekeeper.securityfinding.SecurityFindingController exactly (Sprint 4 Milestone 4). */
@RestController
@RequestMapping("/api/v1/ai-review-runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('WORKSPACE_READ')")
public class AIReviewRunController {

    private final AIReviewRunQueryService aiReviewRunQueryService;

    @GetMapping
    public ApiResponse<PageResponse<AIReviewRunSummaryResponse>> findAll(
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) AIReviewRunStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        AIReviewRunFilter filter = new AIReviewRunFilter(analysisRunId, repositoryId, status, provider, from, to);
        return ApiResponse.ok(PageResponse.from(aiReviewRunQueryService.findPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AIReviewRunDetailResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(aiReviewRunQueryService.findDetailByIdOrThrow(id));
    }
}
