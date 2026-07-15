-- GateKeeper Core - Sprint 3 Milestone 2: Security Engine orchestration integration.
-- Persists SecurityFinding output (Sprint 3 Milestone 1, frozen). Mirrors
-- policy_findings' shape exactly (Security Engine Architecture, Section 12) -
-- same write-once, engine-scoped design ADR-015 established for Policy,
-- applied consistently to Security. Indexed from the start (severity,
-- category, composite analysis_run_id+severity) rather than in a later
-- migration the way policy_findings' were (V3 then V4) - no reason to defer
-- work already known to be needed once the read/query API milestone arrives.

CREATE TABLE security_findings (
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

CREATE INDEX idx_security_findings_analysis_run_id ON security_findings (analysis_run_id);
CREATE INDEX idx_security_findings_severity ON security_findings (severity);
CREATE INDEX idx_security_findings_category ON security_findings (category);
CREATE INDEX idx_security_findings_analysis_run_id_severity ON security_findings (analysis_run_id, severity);
