package com.gatekeeper.role.dto;

import com.gatekeeper.role.Role;
import java.time.Instant;

public record RoleResponse(Long id, String name, String description, Instant createdAt) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.getCreatedAt());
    }
}
