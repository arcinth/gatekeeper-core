-- GateKeeper Core - Sprint 5 Milestone 2: Verdict persistence.
-- Introduces verdicts and verdict_reasons. Deliberately separate tables from
-- analysis_runs/policy_findings/security_findings, but - unlike
-- ai_review_runs - with a UNIQUE constraint on analysis_run_id: a Verdict is
-- computed exactly once, synchronously, in the same transaction that
-- persists Policy/Security findings and marks the AnalysisRun COMPLETED
-- (Sprint 5 Architecture, Section 12 / ADR-039). It is structurally
-- impossible for an AnalysisRun to reach COMPLETED without a Verdict, and
-- impossible for a Verdict to exist for a run that isn't COMPLETED - both
-- facts are written in the same commit or neither is.
--
-- verdicts/verdict_reasons are write-once, like policy_findings/
-- security_findings (no updated_at) - a Verdict is never re-evaluated or
-- edited once written.
--
-- No FK from verdict_reasons to a specific policy_finding/security_finding
-- row (ADR-040): a VerdictReason is a self-contained, human-readable
-- explanation; drill-down is reconstructed via analysis_run_id cross-
-- reference against the existing findings tables, not a direct join.
--
-- No outcome/created_at indexes yet: no REST API or dashboard queries these
-- tables this milestone (same deferred-indexing precedent ai_review_runs
-- already established).

CREATE TABLE verdicts (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_runs (id),
    outcome         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_verdicts_analysis_run_id UNIQUE (analysis_run_id)
);

CREATE TABLE verdict_reasons (
    id         BIGSERIAL PRIMARY KEY,
    verdict_id BIGINT NOT NULL REFERENCES verdicts (id),
    rule_id    VARCHAR(100) NOT NULL,
    blocking   BOOLEAN NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_verdict_reasons_verdict_id ON verdict_reasons (verdict_id);
