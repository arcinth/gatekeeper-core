-- GateKeeper Core - Sprint 2 Milestone 5: read/query API support.
-- The webhook pipeline never needed to filter or group by these columns, so
-- V2/V3 only indexed foreign keys. Milestone 5's list/filter/dashboard-aggregate
-- endpoints add status, created_at, severity, and category as new WHERE/GROUP BY
-- hot paths (Milestone 5 Architecture, Section 10) that have no index to lean on
-- without this migration.

CREATE INDEX idx_analysis_runs_status ON analysis_runs (status);
CREATE INDEX idx_analysis_runs_created_at ON analysis_runs (created_at);

CREATE INDEX idx_policy_findings_severity ON policy_findings (severity);
CREATE INDEX idx_policy_findings_category ON policy_findings (category);
CREATE INDEX idx_policy_findings_analysis_run_id_severity ON policy_findings (analysis_run_id, severity);
