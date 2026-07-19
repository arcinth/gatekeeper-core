-- GateKeeper Core - Milestone 2 (Reviewer Decision Workflow): lets a human
-- record an APPROVE/REJECT decision against an AnalysisRun. Write-once, like
-- verdicts/policy_findings/security_findings (no updated_at): a reviewer
-- changing their mind creates a new row, never edits an old one, so the full
-- decision history stays intact.
--
-- Linked to analysis_run_id, not pull_request_id: a decision is made against
-- one specific run's findings/verdict, and multiple runs can exist per Pull
-- Request over time (the same "latest run per PR" relationship AnalysisRun
-- already has). No UNIQUE constraint on analysis_run_id, unlike verdicts -
-- multiple decisions per run are expected (re-review after discussion), the
-- most recent one is simply the current decision.
--
-- No FK-level ON DELETE CASCADE from reviewer_id to users, matching this
-- schema's existing convention (see verdicts/analysis_runs FKs): a removed
-- user's decision history remains.
--
-- This table intentionally has no effect on analysis_runs/verdicts/
-- pull_requests - recording a decision does not change any of their columns.
-- GitHub Check Run write-back is explicitly out of scope for this milestone.

CREATE TABLE review_decisions (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_runs (id),
    reviewer_id     BIGINT NOT NULL REFERENCES users (id),
    decision        VARCHAR(20) NOT NULL,
    comment         VARCHAR(2000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_decisions_analysis_run_id ON review_decisions (analysis_run_id);
CREATE INDEX idx_review_decisions_reviewer_id ON review_decisions (reviewer_id);
