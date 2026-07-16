package com.gatekeeper.aireviewfinding;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingFilter;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingResponse;
import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
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

/** Mirrors com.gatekeeper.securityfinding.SecurityFindingController exactly (Sprint 4 Milestone 4). */
@RestController
@RequestMapping("/api/v1/ai-review-findings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AIReviewFindingController {

    private final AIReviewFindingQueryService aiReviewFindingQueryService;

    @GetMapping
    public ApiResponse<PageResponse<AIReviewFindingResponse>> findAll(
            @RequestParam(required = false) Long aiReviewRunId,
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) AIReviewConfidence confidence,
            @RequestParam(required = false) AIReviewFindingType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        AIReviewFindingFilter filter =
                new AIReviewFindingFilter(aiReviewRunId, analysisRunId, repositoryId, confidence, type, from, to);
        return ApiResponse.ok(PageResponse.from(aiReviewFindingQueryService.findPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AIReviewFindingResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(aiReviewFindingQueryService.findByIdOrThrow(id));
    }
}
