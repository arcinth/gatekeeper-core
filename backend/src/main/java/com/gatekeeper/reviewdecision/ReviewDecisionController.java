package com.gatekeeper.reviewdecision;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.reviewdecision.dto.CreateReviewDecisionRequest;
import com.gatekeeper.reviewdecision.dto.ReviewDecisionResponse;
import com.gatekeeper.security.SecurityUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A separate controller from AnalysisRunController, sharing its
 * {@code /api/v1/analysis-runs} URL prefix but contributing only the
 * {@code /{id}/review-decisions} sub-resource (Milestone 2: Reviewer
 * Decision Workflow) - the same nested-sub-resource shape ReportController
 * already establishes for this prefix. AnalysisRunController itself is
 * untouched.
 */
@RestController
@RequestMapping("/api/v1/analysis-runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReviewDecisionController {

    private final ReviewDecisionService reviewDecisionService;

    @PostMapping("/{id}/review-decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewDecisionResponse> create(
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewDecisionRequest request,
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(
                "Review decision recorded successfully.",
                reviewDecisionService.create(id, principal.getId(), request));
    }

    @GetMapping("/{id}/review-decisions")
    public ApiResponse<List<ReviewDecisionResponse>> findHistory(@PathVariable Long id) {
        return ApiResponse.ok(reviewDecisionService.findHistory(id));
    }
}
