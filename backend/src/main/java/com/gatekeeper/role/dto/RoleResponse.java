package com.gatekeeper.role.dto;

import com.gatekeeper.role.Role;
import java.time.OffsetDateTime;

public record RoleResponse(Long id, String name, String description, OffsetDateTime createdAt) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.getCreatedAt());
    }
}
