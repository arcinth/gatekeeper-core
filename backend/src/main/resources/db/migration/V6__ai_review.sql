-- GateKeeper Core - Sprint 4 Milestone 3: AI Review orchestration & persistence.
-- Introduces ai_review_runs and ai_review_findings as their own tables,
-- deliberately separate from analysis_runs/policy_findings/security_findings:
-- an AI review run's own lifecycle (COMPLETED/FAILED) is independent of its
-- parent AnalysisRun's - an AI review can fail without ever touching
-- AnalysisRun's own COMPLETED/FAILED transition (Architecture.md Section 3
-- principle 5 / Section 11: AI Review failures must never stop the analysis
-- pipeline or delay a governance decision).
--
-- provider/model/prompt_version are persisted per run, not read from current
-- configuration when queried later - configuration can change over time, so
-- each row must record what was actually used to produce it.
--
-- No status/created_at indexes yet (unlike security_findings, which indexed
-- from the start): no REST API or dashboard queries these tables this
-- milestone, so there is no known hot path to index against yet.

CREATE TABLE ai_review_runs (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_runs (id),
    status          VARCHAR(20) NOT NULL,
    provider        VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    prompt_version  VARCHAR(20) NOT NULL,
    summary         VARCHAR(2000),
    failure_reason  VARCHAR(2000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_review_runs_analysis_run_id ON ai_review_runs (analysis_run_id);

CREATE TABLE ai_review_findings (
    id               BIGSERIAL PRIMARY KEY,
    ai_review_run_id BIGINT NOT NULL REFERENCES ai_review_runs (id),
    type             VARCHAR(20) NOT NULL,
    confidence       VARCHAR(10) NOT NULL,
    file_path        VARCHAR(1000) NOT NULL,
    line_number      INTEGER,
    message          VARCHAR(2000) NOT NULL,
    recommendation   VARCHAR(2000),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_review_findings_ai_review_run_id ON ai_review_findings (ai_review_run_id);
