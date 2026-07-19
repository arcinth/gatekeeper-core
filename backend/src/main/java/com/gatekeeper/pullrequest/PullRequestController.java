package com.gatekeeper.pullrequest;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import com.gatekeeper.pullrequest.dto.PullRequestDetailResponse;
import com.gatekeeper.pullrequest.dto.PullRequestFilter;
import com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Pull Request resource (Milestone 1: Pull Requests as the
 * reviewer's primary workspace). Default sort is updatedAt descending, not
 * createdAt like AnalysisRunController - a reviewer workspace surfaces
 * recently-active PRs first, not recently-opened ones.
 * <p>
 * No write endpoints: Pull Requests are only ever created/updated by
 * PullRequestService.upsert, driven by the pull_request webhook via
 * AnalysisOrchestrator - there is no user-initiated create/edit/delete for
 * this resource.
 */
@RestController
@RequestMapping("/api/v1/pull-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PullRequestController {

    private final PullRequestService pullRequestService;

    @GetMapping
    public ApiResponse<PageResponse<PullRequestSummaryResponse>> findAll(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) PullRequestStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Direction.DESC) Pageable pageable) {
        PullRequestFilter filter = new PullRequestFilter(repositoryId, status);
        return ApiResponse.ok(PageResponse.from(pullRequestService.findSummaryPage(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PullRequestDetailResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(pullRequestService.findDetailByIdOrThrow(id));
    }
}
