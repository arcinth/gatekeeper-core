package com.gatekeeper.repository.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRepositoryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String fullName,
        @Size(max = 1000) String description) {
}
