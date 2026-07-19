package com.gatekeeper.security.authorization;

import com.gatekeeper.role.RoleName;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for what each role may do (Milestone 5: RBAC
 * Enforcement). This is the only place a role name is ever mapped to a
 * capability - see docs/Authorization-Model.md for the full rationale and
 * the process for adding a new permission or role.
 * <p>
 * Keyed by the well-known role names in {@link RoleName}. Role is a
 * manageable RBAC entity (see RoleController), not a closed enum - a role
 * name created through that API which isn't one of the names below resolves
 * to an empty set via {@link #forRole}, i.e. deny-by-default, not an error.
 * A future milestone that lets administrators assign permissions to
 * arbitrary roles would extend or replace this class; it would not change
 * how any controller is annotated, since controllers only ever reference
 * {@link Permission} values, never this class or a role name.
 */
public final class RolePermissions {

    private static final Map<String, Set<Permission>> BY_ROLE = Map.of(
            RoleName.ADMINISTRATOR, Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.REVIEW_DECISION_CREATE,
                    Permission.REPOSITORY_MANAGE,
                    Permission.POLICY_MANAGE,
                    Permission.USER_MANAGE,
                    Permission.ROLE_MANAGE,
                    Permission.AUDIT_LOG_READ),
            RoleName.PLATFORM_ENGINEER, Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.REVIEW_DECISION_CREATE,
                    Permission.REPOSITORY_MANAGE,
                    Permission.POLICY_MANAGE),
            RoleName.DEVSECOPS_ENGINEER, Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.REVIEW_DECISION_CREATE,
                    Permission.AUDIT_LOG_READ),
            RoleName.TECHNICAL_LEAD, Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.REVIEW_DECISION_CREATE),
            RoleName.ENGINEERING_MANAGER, Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.REVIEW_DECISION_CREATE),
            RoleName.DEVELOPER, Set.of(
                    Permission.WORKSPACE_READ));

    private RolePermissions() {
    }

    /** Deny-by-default: a role name outside the well-known set (see class Javadoc), or null, returns an empty set, never throws. */
    public static Set<Permission> forRole(String roleName) {
        if (roleName == null) {
            return Set.of();
        }
        return BY_ROLE.getOrDefault(roleName, Set.of());
    }
}
