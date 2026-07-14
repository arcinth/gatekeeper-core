package com.gatekeeper.analysisrun;

/**
 * Why a particular AnalysisRun exists. Limited to what Milestone 2 actually
 * produces (the pull_request actions AnalysisOrchestrator acts on); a
 * MANUAL_RERUN reason belongs to the future POST /analysis/{id}/rerun endpoint
 * and is deferred until that endpoint is built, per Sprint 2 Architecture,
 * Section 11 - adding it later is a one-line change, not a migration.
 */
public enum AnalysisRunTriggerReason {
    OPENED,
    REOPENED,
    SYNCHRONIZE
}
