package com.gatekeeper.role;

/**
 * Names of the roles seeded by V1__init_schema.sql (docs/API-Design.md "Supported Roles").
 * Role is a manageable RBAC entity (see Role API in docs/API-Design.md), so this is not
 * an exhaustive enum of allowed values - it only names the default seeded set.
 */
public final class RoleName {

    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    public static final String DEVELOPER = "DEVELOPER";
    public static final String TECHNICAL_LEAD = "TECHNICAL_LEAD";
    public static final String ENGINEERING_MANAGER = "ENGINEERING_MANAGER";
    public static final String PLATFORM_ENGINEER = "PLATFORM_ENGINEER";
    public static final String DEVSECOPS_ENGINEER = "DEVSECOPS_ENGINEER";

    private RoleName() {
    }
}
