package com.gatekeeper.repository;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.repository.dto.RepositoryResponse;
import com.gatekeeper.repository.dto.UpdateRepositoryRequest;
import com.gatekeeper.security.SecurityUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code POST}: GitHub App installation is the only supported way a
 * repository enters GateKeeper (Milestone 8: Repository Onboarding) - see
 * {@code com.gatekeeper.github.GitHubInstallationController} and
 * {@code RepositoryService.link}. A manually-created repository would have
 * no {@code githubRepositoryId}/{@code githubInstallation} and could
 * therefore never resolve a real {@code pull_request} webhook
 * (RepositoryLookupService requires both) - update/delete remain, since
 * they're meaningful for any already-linked repository.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('WORKSPACE_READ')")
public class RepositoryController {

    private final RepositoryService repositoryService;

    @GetMapping
    public ApiResponse<List<RepositoryResponse>> findAll() {
        List<RepositoryResponse> repositories = repositoryService.findAll().stream()
                .map(RepositoryResponse::from)
                .toList();
        return ApiResponse.ok(repositories);
    }

    @GetMapping("/{id}")
    public ApiResponse<RepositoryResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(RepositoryResponse.from(repositoryService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('REPOSITORY_MANAGE')")
    public ApiResponse<RepositoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRepositoryRequest request,
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok("Repository updated successfully.",
                RepositoryResponse.from(repositoryService.update(id, request, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('REPOSITORY_MANAGE')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        repositoryService.delete(id, principal.getId());
    }
}
