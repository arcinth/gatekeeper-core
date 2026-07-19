package com.gatekeeper.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.role.RoleName;
import org.junit.jupiter.api.Test;

/**
 * Exhaustively pins the role -> permission matrix approved across Milestone 5
 * (RBAC Enforcement), Milestone 6 (Policy Management), and Milestone 7
 * (Enterprise Audit Logging). If this test needs to change,
 * docs/Authorization-Model.md's matrix table must change with it - they are
 * two views of the same fact.
 */
class RolePermissionsTest {

    @Test
    void administrator_hasEveryPermission() {
        assertThat(RolePermissions.forRole(RoleName.ADMINISTRATOR)).containsExactlyInAnyOrder(
                Permission.WORKSPACE_READ,
                Permission.REVIEW_DECISION_CREATE,
                Permission.REPOSITORY_MANAGE,
                Permission.POLICY_MANAGE,
                Permission.USER_MANAGE,
                Permission.ROLE_MANAGE,
                Permission.AUDIT_LOG_READ);
    }

    @Test
    void platformEngineer_canReadReviewManageRepositoriesAndPolicies_butNotUsersOrRoles() {
        assertThat(RolePermissions.forRole(RoleName.PLATFORM_ENGINEER)).containsExactlyInAnyOrder(
                Permission.WORKSPACE_READ,
                Permission.REVIEW_DECISION_CREATE,
                Permission.REPOSITORY_MANAGE,
                Permission.POLICY_MANAGE);
    }

    @Test
    void devSecOpsEngineer_canReadReviewAndAudit_butNotManageAnything() {
        assertThat(RolePermissions.forRole(RoleName.DEVSECOPS_ENGINEER)).containsExactlyInAnyOrder(
                Permission.WORKSPACE_READ, Permission.REVIEW_DECISION_CREATE, Permission.AUDIT_LOG_READ);
    }

    @Test
    void technicalLead_canReadAndReview_butNotManageAnything() {
        assertThat(RolePermissions.forRole(RoleName.TECHNICAL_LEAD)).containsExactlyInAnyOrder(
                Permission.WORKSPACE_READ, Permission.REVIEW_DECISION_CREATE);
    }

    @Test
    void engineeringManager_canReadAndReview_butNotManageAnything() {
        assertThat(RolePermissions.forRole(RoleName.ENGINEERING_MANAGER)).containsExactlyInAnyOrder(
                Permission.WORKSPACE_READ, Permission.REVIEW_DECISION_CREATE);
    }

    @Test
    void developer_canOnlyRead() {
        assertThat(RolePermissions.forRole(RoleName.DEVELOPER)).containsExactly(Permission.WORKSPACE_READ);
    }

    @Test
    void unknownOrCustomRoleName_resolvesToNoPermissionsRatherThanThrowing() {
        assertThat(RolePermissions.forRole("SOME_FUTURE_CUSTOM_ROLE")).isEmpty();
        assertThat(RolePermissions.forRole(null)).isEmpty();
    }
}
