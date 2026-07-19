-- Milestone 7: Enterprise Audit Logging.
-- Extends the write-once audit_logs table (introduced for
-- ENGINEERING_REPORT_PUBLISHED only) with the structured dimensions needed to
-- make it the authoritative history of governance actions across the
-- platform: who (actor), what kind of thing was acted on (target), what
-- changed (old/new value), and enough scope columns to answer "which
-- repository/pull request/analysis run" without joining through
-- analysis_runs for every event type.
--
-- All new columns are nullable: existing ENGINEERING_REPORT_PUBLISHED rows
-- predate this migration and are never backfilled - a null actor/target/etc.
-- on those historical rows is expected, not a data-quality gap to fix.

ALTER TABLE audit_logs
    ADD COLUMN repository_id BIGINT REFERENCES repositories (id),
    ADD COLUMN pull_request_id BIGINT REFERENCES pull_requests (id),
    ADD COLUMN actor_id BIGINT REFERENCES users (id),
    ADD COLUMN target_type VARCHAR(50),
    ADD COLUMN target_id VARCHAR(100),
    ADD COLUMN old_value TEXT,
    ADD COLUMN new_value TEXT,
    ADD COLUMN correlation_id VARCHAR(100);

-- organization_id is on every query (every audit read is organization-scoped)
-- and occurred_at DESC is the default sort for the search/export API. V8
-- already indexed organization_id and analysis_run_id individually; this adds
-- the composite used for the paginated search's default sort plus indexes for
-- the new filterable columns.
CREATE INDEX idx_audit_logs_organization_occurred_at ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_repository_id ON audit_logs (repository_id);
CREATE INDEX idx_audit_logs_pull_request_id ON audit_logs (pull_request_id);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs (event_type);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);
