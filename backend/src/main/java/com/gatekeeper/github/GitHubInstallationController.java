package com.gatekeeper.github;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.github.dto.GitHubInstallationResponse;
import com.gatekeeper.github.dto.InstallUrlResponse;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.security.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Repository Onboarding read/trigger surface (Milestone 8): visibility
 * into GitHub App installations, the URL that starts GitHub's own install
 * flow, and a synchronous resync trigger. Read endpoints use
 * {@code WORKSPACE_READ} - installation visibility is informational, the
 * same transparency posture RepositoryController already gives every role
 * for repositories themselves (see docs/Authorization-Model.md). The two
 * action endpoints ({@code install-url}, {@code sync}) require
 * {@code REPOSITORY_MANAGE}: starting or re-triggering a GitHub connection is
 * the same capability as managing repository connections, not a new one.
 * <p>
 * Deliberately reads {@code RepositoryRepository} directly (not through
 * {@code RepositoryService}) only for the read-only repository count per
 * installation - the minimal dependency for that one composition, rather
 * than pulling in RepositoryService's write surface.
 */
@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GitHubInstallationController {

    private final GitHubInstallationService gitHubInstallationService;
    private final GitHubRepositorySyncService gitHubRepositorySyncService;
    private final RepositoryRepository repositoryRepository;
    private final RateLimitService rateLimitService;

    @Value("${gatekeeper.github.app.id}")
    private long appId;

    @Value("${gatekeeper.github.app.slug:}")
    private String appSlug;

    /**
     * The GitHub App's id/slug never leave the backend - only the fully-formed
     * URL (or {@code appConfigured=false}) is exposed, so the frontend never
     * needs to know either value.
     */
    @GetMapping("/install-url")
    @PreAuthorize("hasAuthority('REPOSITORY_MANAGE')")
    public ApiResponse<InstallUrlResponse> getInstallUrl() {
        boolean configured = appId != 0 && appSlug != null && !appSlug.isBlank();
        String url = configured ? "https://github.com/apps/" + appSlug + "/installations/new" : null;
        return ApiResponse.ok(new InstallUrlResponse(url, configured));
    }

    @GetMapping("/installations")
    @PreAuthorize("hasAuthority('WORKSPACE_READ')")
    public ApiResponse<List<GitHubInstallationResponse>> findAll() {
        List<GitHubInstallationResponse> installations = gitHubInstallationService.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(installations);
    }

    @GetMapping("/installations/{id}")
    @PreAuthorize("hasAuthority('WORKSPACE_READ')")
    public ApiResponse<GitHubInstallationResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(gitHubInstallationService.findByIdOrThrow(id)));
    }

    /** Synchronously re-runs the same reconciliation GitHubRepositorySyncService already runs asynchronously after every "installation" webhook. */
    @PostMapping("/installations/{id}/sync")
    @PreAuthorize("hasAuthority('REPOSITORY_MANAGE')")
    public ApiResponse<GitHubInstallationResponse> sync(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        // Rate-limited per caller, not per installation (Milestone 10: Security Hardening) -
        // this protects against one user's repeated-click loop, not a shared installation resource.
        rateLimitService.checkRepositorySync(principal.getId());
        GitHubInstallation installation = gitHubInstallationService.findByIdOrThrow(id);
        gitHubRepositorySyncService.synchronize(installation.getInstallationId());
        return ApiResponse.ok(
                "Repository synchronization completed.",
                toResponse(gitHubInstallationService.findByIdOrThrow(id)));
    }

    private GitHubInstallationResponse toResponse(GitHubInstallation installation) {
        return GitHubInstallationResponse.from(
                installation, repositoryRepository.countByGithubInstallationId(installation.getId()));
    }
}
