-- GateKeeper Core - Sprint 2 Milestone 4: Policy Engine integration.
-- Persists PolicyFinding output (Milestone 3, frozen) and records why an
-- AnalysisRun failed, if it did. Scoped to Policy Engine specifically rather
-- than a generic polymorphic findings table (Milestone 4 Architecture,
-- ADR-015) - consistent with Milestone 3's decision to defer the shared
-- Finding/AnalysisEngine abstraction until a second engine exists.

ALTER TABLE analysis_runs
    ADD COLUMN failure_reason VARCHAR(2000);

CREATE TABLE policy_findings (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_runs (id),
    rule_id         VARCHAR(100) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    file_path       VARCHAR(1000) NOT NULL,
    line_number     INTEGER NOT NULL,
    message         VARCHAR(2000) NOT NULL,
    recommendation  VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_findings_analysis_run_id ON policy_findings (analysis_run_id);
