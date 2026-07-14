package com.gatekeeper.pullrequest;

/**
 * Source-agnostic description of a Pull Request state to persist. Deliberately
 * not GitHub's webhook DTO: PullRequestService shouldn't need to know GitHub's
 * JSON shape, only what a Pull Request looks like. The caller (currently
 * AnalysisOrchestrator, translating a GitHub webhook payload) owns that mapping.
 */
public record PullRequestUpsertCommand(
        Long githubPrId,
        Integer number,
        String title,
        String authorLogin,
        String sourceBranch,
        String targetBranch,
        String headSha,
        String githubState,
        boolean merged) {
}
