package com.gatekeeper.verdict;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import com.gatekeeper.verdict.dto.VerdictDetailResponse;
import com.gatekeeper.verdict.dto.VerdictFilter;
import com.gatekeeper.verdict.dto.VerdictSummaryResponse;
import com.gatekeeper.verdictengine.VerdictOutcome;
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

/** Mirrors com.gatekeeper.aireviewrun.AIReviewRunController exactly (Sprint 5 Milestone 3). */
@RestController
@RequestMapping("/api/v1/verdicts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VerdictController {

    private final VerdictQueryService verdictQueryService;

    @GetMapping
    public ApiResponse<PageResponse<VerdictSummaryResponse>> findAll(
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) VerdictOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        VerdictFilter filter = new VerdictFilter(analysisRunId, repositoryId, outcome, from, to);
        return ApiResponse.ok(PageResponse.from(verdictQueryService.findPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<VerdictDetailResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(verdictQueryService.findDetailByIdOrThrow(id));
    }
}
