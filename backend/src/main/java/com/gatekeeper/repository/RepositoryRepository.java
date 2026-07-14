package com.gatekeeper.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    boolean existsByFullNameIgnoreCase(String fullName);

    Optional<Repository> findByGithubRepositoryId(Long githubRepositoryId);
}
