-- GateKeeper Core - GitHub App installation onboarding: persists the account
-- and permission details GitHub sends on the "installation" webhook event, and
-- tracks whether an installation is still active (App uninstalled). Nullable:
-- existing rows predate this event handling. installation_repositories (the
-- per-repository selection payload) remains out of scope.

ALTER TABLE github_installations
    ADD COLUMN github_account_id    BIGINT,
    ADD COLUMN github_account_type  VARCHAR(20),
    ADD COLUMN repository_selection VARCHAR(20),
    ADD COLUMN permissions          TEXT,
    ADD COLUMN active               BOOLEAN NOT NULL DEFAULT TRUE;
