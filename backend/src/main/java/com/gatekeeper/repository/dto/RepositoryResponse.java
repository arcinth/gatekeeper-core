package com.gatekeeper.repository.dto;

import com.gatekeeper.repository.Repository;
import java.time.Instant;

public record RepositoryResponse(
        Long id,
        String name,
        String fullName,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static RepositoryResponse from(Repository repository) {
        return new RepositoryResponse(
                repository.getId(),
                repository.getName(),
                repository.getFullName(),
                repository.getDescription(),
                repository.isActive(),
                repository.getCreatedAt(),
                repository.getUpdatedAt());
    }
}
