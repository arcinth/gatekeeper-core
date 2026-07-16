-- GateKeeper Core - Unified Engineering Report Architecture, Milestone 1.
-- Introduces engineering_reports and audit_logs.
--
-- engineering_reports is deliberately a thin publication marker, not a copy
-- of Policy/Security/AI findings or the Verdict itself (extends ADR-040's
-- "no duplication, cross-reference by analysis_run_id" precedent to the
-- Report - ADR-044). UNIQUE(analysis_run_id) mirrors verdicts' own
-- constraint (ADR-039): exactly one report per AnalysisRun, ever, enforced
-- at the database level, not just call-site discipline - ReportPublicationService
-- relies on this constraint to resolve the race between its two independent
-- trigger paths (Verdict produced vs. AI review finished) safely (ADR-045).
--
-- ai_review_status is the one piece of publication-time context worth
-- persisting on the report itself (INCLUDED/UNAVAILABLE/DISABLED) - it
-- answers "why is the AI section empty?" without the reader needing to
-- reason about the join timing that produced it (ADR-050).
--
-- Write-once, like verdicts/verdict_reasons (no updated_at): a report is
-- never re-published or edited once written.

CREATE TABLE engineering_reports (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT NOT NULL REFERENCES analysis_runs (id),
    ai_review_status VARCHAR(20) NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_engineering_reports_analysis_run_id UNIQUE (analysis_run_id)
);

-- Supports ReportTimeoutSweepJob's query (verdicts older than a grace window
-- with no matching engineering_reports row) - the first query against
-- verdicts.created_at, so unlike V7's deferred-indexing precedent, this one
-- has a real, known hot path from day one.
CREATE INDEX idx_verdicts_created_at ON verdicts (created_at);

-- audit_logs is introduced as small, generic, reusable infrastructure
-- (ADR-049) - this milestone wires exactly one producer
-- (ENGINEERING_REPORT_PUBLISHED, written by ReportPublicationService in the
-- same transaction as its engineering_reports row), not a retrofit of every
-- historical event type docs/Database.md names as an example.
--
-- analysis_run_id is nullable (unlike engineering_reports' required, unique
-- one) because not every future event type will be analysis-run-scoped
-- (e.g. "Repository connected"). Immutable: no updated_at, no update path.

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    analysis_run_id BIGINT REFERENCES analysis_runs (id),
    event_type      VARCHAR(50) NOT NULL,
    summary         VARCHAR(1000) NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_analysis_run_id ON audit_logs (analysis_run_id);
CREATE INDEX idx_audit_logs_organization_id ON audit_logs (organization_id);
