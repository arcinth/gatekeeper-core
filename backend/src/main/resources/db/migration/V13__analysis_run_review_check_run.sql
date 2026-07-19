-- GateKeeper Core - Milestone 4: GitHub Check Run write-back for reviewer
-- decisions. Mirrors V11__analysis_run_check_run.sql's github_check_run_id
-- column exactly, but for a separate check run ("GateKeeper Review") that
-- publishes a ReviewDecision - deliberately a distinct column from
-- github_check_run_id, which remains exclusively owned by the Verdict-driven
-- check run (GitHubCheckRunService). Keeping the two ids in separate columns
-- is what makes it structurally impossible for reviewer-decision publication
-- to ever touch the Verdict's own check run.

ALTER TABLE analysis_runs
    ADD COLUMN github_review_check_run_id BIGINT;
