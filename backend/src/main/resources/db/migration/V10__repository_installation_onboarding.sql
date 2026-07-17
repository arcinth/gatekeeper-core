-- GateKeeper Core - GitHub App installation_repositories onboarding: adds the
-- repository owner login (derived from full_name, e.g. "owner/repo") so it
-- doesn't need to be re-parsed out of full_name every time it's needed.
-- Nullable: existing rows (including Sprint 1's manually-created ones) predate it.

ALTER TABLE repositories
    ADD COLUMN owner VARCHAR(255);
