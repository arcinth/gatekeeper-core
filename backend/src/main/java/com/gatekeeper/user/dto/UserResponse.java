package com.gatekeeper.user.dto;

import com.gatekeeper.user.User;
import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String roleName,
        boolean enabled,
        OffsetDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
