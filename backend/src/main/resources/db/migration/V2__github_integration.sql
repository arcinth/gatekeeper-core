-- GateKeeper Core - Sprint 2 Milestone 2: GitHub webhook ingestion schema.
-- Adds installation tracking, links Repository to GitHub, and introduces
-- PullRequest + AnalysisRun (docs/Database.md's full model; Finding, Verdict,
-- EngineeringReport, PolicyDefinition, and AuditLog remain out of scope until
-- the Policy/Security/AI Engine and Verdict Engine milestones).

CREATE TABLE github_installations (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations (id),
    installation_id       BIGINT NOT NULL,
    github_account_login  VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_github_installations_installation_id UNIQUE (installation_id)
);

CREATE INDEX idx_github_installations_organization_id ON github_installations (organization_id);

-- Nullable: Sprint 1's manually-created repositories have no GitHub linkage,
-- and repository onboarding via the installation webhook family is not yet
-- implemented (Milestone 2 only resolves existing links, it doesn't create them).
ALTER TABLE repositories
    ADD COLUMN github_repository_id   BIGINT,
    ADD COLUMN github_installation_id BIGINT REFERENCES github_installations (id),
    ADD COLUMN default_branch         VARCHAR(255);

-- A plain UNIQUE constraint is sufficient here: Postgres treats each NULL as
-- distinct, so any number of un-linked (NULL) repositories remain permitted.
ALTER TABLE repositories
    ADD CONSTRAINT uq_repositories_github_repository_id UNIQUE (github_repository_id);

CREATE INDEX idx_repositories_github_installation_id ON repositories (github_installation_id);

CREATE TABLE pull_requests (
    id             BIGSERIAL PRIMARY KEY,
    repository_id  BIGINT NOT NULL REFERENCES repositories (id),
    github_pr_id   BIGINT NOT NULL,
    pr_number      INTEGER NOT NULL,
    title          VARCHAR(1000) NOT NULL,
    author_login   VARCHAR(255) NOT NULL,
    source_branch  VARCHAR(500) NOT NULL,
    target_branch  VARCHAR(500) NOT NULL,
    head_sha       VARCHAR(40) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_pull_requests_github_pr_id UNIQUE (github_pr_id)
);

CREATE INDEX idx_pull_requests_repository_id ON pull_requests (repository_id);

CREATE TABLE analysis_runs (
    id              BIGSERIAL PRIMARY KEY,
    pull_request_id BIGINT NOT NULL REFERENCES pull_requests (id),
    commit_sha      VARCHAR(40) NOT NULL,
    trigger_reason  VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- Enforces ingestion idempotency at the database level: a redelivered webhook
    -- for a commit already recorded cannot produce a second AnalysisRun, even
    -- under concurrent delivery (see AnalysisRunService's race-recovery logic).
    CONSTRAINT uq_analysis_runs_pull_request_commit UNIQUE (pull_request_id, commit_sha)
);

CREATE INDEX idx_analysis_runs_pull_request_id ON analysis_runs (pull_request_id);
CREATE INDEX idx_analysis_runs_commit_sha ON analysis_runs (commit_sha);
