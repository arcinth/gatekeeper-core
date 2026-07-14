package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import java.util.Arrays;
import java.util.Optional;

/**
 * Maps GitHub's raw pull_request webhook "action" string to what
 * AnalysisOrchestrator should do about it. Actions GateKeeper doesn't act on
 * (labeled, assigned, review_requested, ...) simply have no corresponding
 * constant, which is how GitHubEventRouter recognizes them as no-ops.
 */
enum PullRequestAction {

    OPENED("opened", AnalysisRunTriggerReason.OPENED),
    REOPENED("reopened", AnalysisRunTriggerReason.REOPENED),
    SYNCHRONIZE("synchronize", AnalysisRunTriggerReason.SYNCHRONIZE),
    CLOSED("closed", null);

    private final String githubAction;
    private final AnalysisRunTriggerReason triggerReason;

    PullRequestAction(String githubAction, AnalysisRunTriggerReason triggerReason) {
        this.githubAction = githubAction;
        this.triggerReason = triggerReason;
    }

    static Optional<PullRequestAction> fromGitHubAction(String githubAction) {
        return Arrays.stream(values())
                .filter(action -> action.githubAction.equals(githubAction))
                .findFirst();
    }

    boolean createsAnalysisRun() {
        return triggerReason != null;
    }

    AnalysisRunTriggerReason triggerReason() {
        if (triggerReason == null) {
            throw new IllegalStateException(this + " does not create an AnalysisRun and has no trigger reason.");
        }
        return triggerReason;
    }
}
