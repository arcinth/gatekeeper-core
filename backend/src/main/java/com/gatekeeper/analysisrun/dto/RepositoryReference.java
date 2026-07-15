package com.gatekeeper.analysisrun.dto;

import com.gatekeeper.repository.Repository;

/** Minimal repository context embedded in AnalysisRun responses - not the full RepositoryResponse shape. */
public record RepositoryReference(Long id, String fullName) {

    public static RepositoryReference from(Repository repository) {
        return new RepositoryReference(repository.getId(), repository.getFullName());
    }
}
