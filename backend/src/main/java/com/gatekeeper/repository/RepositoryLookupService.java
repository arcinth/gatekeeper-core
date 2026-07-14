package com.gatekeeper.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the GateKeeper Repository a GitHub webhook refers to. A webhook for
 * a repository GateKeeper doesn't know about, or one it knows about but hasn't
 * finished linking to a GitHub App installation, is an expected occurrence in a
 * multi-tenant webhook receiver - not an error - so this returns empty rather
 * than throwing (see Sprint 2 Architecture, Section 13: Error Handling Strategy).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryLookupService {

    private final RepositoryRepository repositoryRepository;

    public Optional<Repository> findLinkedRepository(long githubRepositoryId) {
        Optional<Repository> repository = repositoryRepository.findByGithubRepositoryId(githubRepositoryId);

        if (repository.isEmpty()) {
            log.info("No GateKeeper repository is linked to GitHub repository id {}.", githubRepositoryId);
            return Optional.empty();
        }

        if (repository.get().getGithubInstallation() == null) {
            log.warn("GitHub repository id {} resolves to repository '{}' but it has no linked installation.",
                    githubRepositoryId, repository.get().getFullName());
            return Optional.empty();
        }

        return repository;
    }
}
