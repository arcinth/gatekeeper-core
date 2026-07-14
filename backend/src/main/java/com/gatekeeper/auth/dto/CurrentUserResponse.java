package com.gatekeeper.auth.dto;

import com.gatekeeper.user.User;

public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        String roleName,
        Long organizationId,
        String organizationName) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getOrganization().getId(),
                user.getOrganization().getName());
    }
}
