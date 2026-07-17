package com.gatekeeper.repository;

import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload.RepositoryReference;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.repository.dto.CreateRepositoryRequest;
import com.gatekeeper.repository.dto.UpdateRepositoryRequest;
import java.util.List;
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

    public List<Repository> findAll() {
        return repositoryRepository.findAll();
    }

    public Repository findById(Long id) {
        return repositoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + id));
    }

    @Transactional
    public Repository create(CreateRepositoryRequest request) {
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
        return repositoryRepository.save(repository);
    }

    @Transactional
    public Repository update(Long id, UpdateRepositoryRequest request) {
        Repository repository = findById(id);
        repository.setName(request.name());
        repository.setDescription(request.description());
        repository.setActive(request.active());
        return repositoryRepository.save(repository);
    }

    @Transactional
    public void delete(Long id) {
        Repository repository = findById(id);
        repositoryRepository.delete(repository);
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
            link(installation, added, deliveryId);
        }
        for (RepositoryReference removed : orEmpty(payload.repositoriesRemoved())) {
            unlink(removed, deliveryId);
        }
    }

    private void link(GitHubInstallation installation, RepositoryReference added, String deliveryId) {
        Repository repository = repositoryRepository.findByGithubRepositoryId(added.id())
                .orElseGet(() -> Repository.builder()
                        .organization(organizationService.getDefaultOrganization())
                        .githubRepositoryId(added.id())
                        .build());

        repository.setName(added.name());
        repository.setFullName(added.fullName());
        repository.setOwner(ownerFrom(added.fullName()));
        repository.setGithubInstallation(installation);
        repository.setActive(true);
        repositoryRepository.save(repository);
        log.info("Repository '{}' linked to installation {} (delivery {}).",
                added.fullName(), installation.getInstallationId(), deliveryId);
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
