-- GateKeeper Core - Sprint 2: GitHub Check Run publishing. Remembers the
-- GitHub-side check run id once created, so a later publish attempt for the
-- same analysis run updates it instead of creating a duplicate. Nullable:
-- existing rows predate this feature, and a run whose repository has no
-- linked GitHub installation never gets one at all.

ALTER TABLE analysis_runs
    ADD COLUMN github_check_run_id BIGINT;
