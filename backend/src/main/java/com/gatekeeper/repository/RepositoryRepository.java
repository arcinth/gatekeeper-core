package com.gatekeeper.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    boolean existsByFullNameIgnoreCase(String fullName);
}
