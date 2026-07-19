package com.gatekeeper.repository;

import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.dto.InstallationRepositoriesResponse;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload.RepositoryReference;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.repository.dto.CreateRepositoryRequest;
import com.gatekeeper.repository.dto.UpdateRepositoryRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final GitHubInstallationRepository gitHubInstallationRepository;
    private final OrganizationService organizationService;
    private final AuditLogService auditLogService;

    public List<Repository> findAll() {
        return repositoryRepository.findAll();
    }

    public Repository findById(Long id) {
        return repositoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + id));
    }

    @Transactional
    public Repository create(CreateRepositoryRequest request, Long actorId) {
        if (repositoryRepository.existsByFullNameIgnoreCase(request.fullName())) {
            throw new ConflictException("A repository named '" + request.fullName() + "' already exists.");
        }
        Repository repository = Repository.builder()
                .organization(organizationService.getDefaultOrganization())
                .name(request.name())
                .fullName(request.fullName())
                .description(request.description())
                .active(true)
                .build();
        Repository saved = repositoryRepository.save(repository);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.REPOSITORY_CONNECTED)
                .organizationId(saved.getOrganization().getId())
                .repositoryId(saved.getId())
                .actorId(actorId)
                .newValue(Map.of("name", saved.getName(), "fullName", saved.getFullName(), "active", true))
                .summary("Repository '" + saved.getFullName() + "' connected.")
                .build());

        return saved;
    }

    @Transactional
    public Repository update(Long id, UpdateRepositoryRequest request, Long actorId) {
        Repository repository = findById(id);
        Map<String, Object> oldValue = Map.of(
                "name", repository.getName(),
                "description", repository.getDescription() == null ? "" : repository.getDescription(),
                "active", repository.isActive());

        repository.setName(request.name());
        repository.setDescription(request.description());
        repository.setActive(request.active());
        Repository saved = repositoryRepository.save(repository);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.REPOSITORY_UPDATED)
                .organizationId(saved.getOrganization().getId())
                .repositoryId(saved.getId())
                .actorId(actorId)
                .oldValue(oldValue)
                .newValue(Map.of(
                        "name", saved.getName(),
                        "description", saved.getDescription() == null ? "" : saved.getDescription(),
                        "active", saved.isActive()))
                .summary("Repository '" + saved.getFullName() + "' updated.")
                .build());

        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        Repository repository = findById(id);
        Long organizationId = repository.getOrganization().getId();
        String fullName = repository.getFullName();
        repositoryRepository.delete(repository);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.REPOSITORY_REMOVED)
                .organizationId(organizationId)
                .actorId(actorId)
                .oldValue(Map.of("name", fullName))
                .summary("Repository '" + fullName + "' removed.")
                .build());
    }

    /**
     * Handles the "installation_repositories" webhook: links each newly
     * selected repository to the installation (creating a Repository row if
     * GateKeeper has never seen this GitHub repository before, or reactivating
     * one it already knows), and marks each deselected repository inactive.
     * Deactivating rather than deleting mirrors Repository.active's existing
     * soft-disable pattern (and GitHubInstallation.active's, for the same
     * reason) - the repository can be found again by github_repository_id and
     * reactivated cleanly if it's re-added to the installation later, and
     * nothing referencing it (analysis runs, findings, verdicts) is orphaned.
     */
    @Transactional
    public void handleInstallationRepositoriesEvent(InstallationRepositoriesWebhookPayload payload, String deliveryId) {
        Long installationId = payload.installation().id();
        GitHubInstallation installation = gitHubInstallationRepository.findByInstallationId(installationId)
                .orElse(null);
        if (installation == null) {
            log.info("Ignoring installation_repositories event for unknown installation {} (delivery {}).",
                    installationId, deliveryId);
            return;
        }

        for (RepositoryReference added : orEmpty(payload.repositoriesAdded())) {
            link(installation, added.id(), added.name(), added.fullName(), deliveryId);
        }
        for (RepositoryReference removed : orEmpty(payload.repositoriesRemoved())) {
            unlink(removed, deliveryId);
        }
    }

    /**
     * Synchronizes an installation's full repository list from GitHub's own
     * GET /installation/repositories response (see GitHubRepositorySyncService,
     * which calls this after every "installation" webhook) - the authoritative
     * catch-up for repositories the installation_repositories webhook alone
     * cannot be relied on to have delivered, particularly the initial set at
     * install time. Reuses the exact same link() logic the
     * installation_repositories webhook path uses, so a repository ends up in
     * the same state regardless of which of the two paths reported it.
     */
    @Transactional
    public void synchronizeFromInstallation(
            Long installationId, List<InstallationRepositoriesResponse.RepositorySummary> repositories) {
        GitHubInstallation installation = gitHubInstallationRepository.findByInstallationId(installationId)
                .orElse(null);
        if (installation == null) {
            log.info("Skipping repository synchronization for unknown installation {}.", installationId);
            return;
        }

        for (InstallationRepositoriesResponse.RepositorySummary repository : repositories) {
            link(installation, repository.id(), repository.name(), repository.fullName(), "installation-sync");
        }
        log.info("Synchronized {} repository(ies) for installation {}.", repositories.size(), installationId);
    }

    private void link(GitHubInstallation installation, Long githubRepositoryId, String name, String fullName,
            String deliveryId) {
        Repository repository = repositoryRepository.findByGithubRepositoryId(githubRepositoryId)
                .orElseGet(() -> Repository.builder()
                        .organization(organizationService.getDefaultOrganization())
                        .githubRepositoryId(githubRepositoryId)
                        .build());

        repository.setName(name);
        repository.setFullName(fullName);
        repository.setOwner(ownerFrom(fullName));
        repository.setGithubInstallation(installation);
        repository.setActive(true);
        repositoryRepository.save(repository);
        log.info("Repository '{}' linked to installation {} (delivery {}).",
                fullName, installation.getInstallationId(), deliveryId);
    }

    private void unlink(RepositoryReference removed, String deliveryId) {
        repositoryRepository.findByGithubRepositoryId(removed.id()).ifPresentOrElse(
                repository -> {
                    repository.setActive(false);
                    repositoryRepository.save(repository);
                    log.info("Repository '{}' marked inactive after installation_repositories removal (delivery {}).",
                            repository.getFullName(), deliveryId);
                },
                () -> log.info("Ignoring removal of unknown repository (github id {}) (delivery {}).",
                        removed.id(), deliveryId));
    }

    private static String ownerFrom(String fullName) {
        if (fullName == null) {
            return null;
        }
        int slash = fullName.indexOf('/');
        return slash > 0 ? fullName.substring(0, slash) : fullName;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
