-- GateKeeper Core - Sprint 1 Foundation Schema
-- Entities: Organization, Role, User, RefreshToken, Repository
-- Organization/Role/Repository/AnalysisRun/Finding/Verdict/EngineeringReport/AuditLog/PolicyDefinition
-- are the full domain model per docs/Database.md. Sprint 1 implements only the subset
-- required for authentication, RBAC, and local repository management.

CREATE TABLE organizations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_organizations_name UNIQUE (name)
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    role_id         BIGINT NOT NULL REFERENCES roles (id),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_role_id ON users (role_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE repositories (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    name            VARCHAR(255) NOT NULL,
    full_name       VARCHAR(500) NOT NULL,
    description     VARCHAR(1000),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_repositories_org_full_name UNIQUE (organization_id, full_name)
);

CREATE INDEX idx_repositories_organization_id ON repositories (organization_id);

-- Reference data seeding (roles + a single default organization).
-- This is bootstrap/reference data required for the platform to function,
-- not sample business data.

INSERT INTO organizations (name) VALUES ('Default Organization');

INSERT INTO roles (name, description) VALUES
    ('ADMINISTRATOR',      'Full administrative access to GateKeeper.'),
    ('DEVELOPER',          'Software Developer.'),
    ('TECHNICAL_LEAD',     'Technical Lead.'),
    ('ENGINEERING_MANAGER','Engineering Manager.'),
    ('PLATFORM_ENGINEER',  'Platform Engineer.'),
    ('DEVSECOPS_ENGINEER', 'DevSecOps Engineer.');
