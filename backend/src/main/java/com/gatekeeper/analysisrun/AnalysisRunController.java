package com.gatekeeper.analysisrun;

import com.gatekeeper.analysisrun.dto.AnalysisRunDetailResponse;
import com.gatekeeper.analysisrun.dto.AnalysisRunFilter;
import com.gatekeeper.analysisrun.dto.AnalysisRunSummaryResponse;
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

@RestController
@RequestMapping("/api/v1/analysis-runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AnalysisRunController {

    private final AnalysisRunService analysisRunService;

    @GetMapping
    public ApiResponse<PageResponse<AnalysisRunSummaryResponse>> findAll(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) AnalysisRunStatus status,
            @RequestParam(required = false) AnalysisRunTriggerReason triggerReason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        AnalysisRunFilter filter = new AnalysisRunFilter(repositoryId, status, triggerReason, from, to);
        return ApiResponse.ok(PageResponse.from(analysisRunService.findSummaryPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnalysisRunDetailResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(analysisRunService.findDetailByIdOrThrow(id));
    }
}
