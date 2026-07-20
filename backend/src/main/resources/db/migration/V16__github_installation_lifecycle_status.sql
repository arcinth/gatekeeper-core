-- Milestone 8: Repository Onboarding.
-- Adds a UI-facing repository-synchronization health status to
-- github_installations, distinct from the existing "active" column (which
-- only answers "does this installation still exist on GitHub"). Backfilled
-- from the existing active column so pre-existing installations get a
-- reasonable status without waiting for their next sync.

ALTER TABLE github_installations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONNECTING',
    ADD COLUMN last_successful_sync_at TIMESTAMPTZ,
    ADD COLUMN last_sync_error VARCHAR(1000);

UPDATE github_installations SET status = 'ACTIVE' WHERE active = true;
UPDATE github_installations SET status = 'DISCONNECTED' WHERE active = false;
