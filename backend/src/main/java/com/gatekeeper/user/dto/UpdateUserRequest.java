package com.gatekeeper.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotNull Long roleId,
        @NotNull Boolean enabled) {
}
