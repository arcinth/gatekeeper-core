package com.gatekeeper.aireviewengine;

import java.util.List;

/**
 * Everything an AIReviewProvider needs to review one AnalysisRun. Structurally
 * related to PolicyContext/SecurityContext (same added-lines-only ChangedFile
 * shape, same ADR-014 PR-scoping principle reused a third time) but
 * deliberately wider: pullRequestTitle and targetBranch have no equivalent in
 * the deterministic contexts, because prompt quality genuinely benefits from
 * PR intent in a way pure pattern matching never needs (Sprint 4 Architecture,
 * Section 7). This is an intentional divergence in shape, not an
 * inconsistency with the frozen deterministic engines.
 *
 * @param analysisRunId      identifies which AnalysisRun this review belongs to
 * @param repositoryFullName the "org/repo" the AnalysisRun's PullRequest belongs to
 * @param pullRequestNumber  the PR number, for prompt context and traceability
 * @param pullRequestTitle   the PR title, for prompt context
 * @param targetBranch       the PR's target branch, for prompt context
 * @param changedFiles       the file contents to review - added lines only
 */
public record AIReviewContext(
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String pullRequestTitle,
        String targetBranch,
        List<ChangedFile> changedFiles) {

    public record ChangedFile(String path, String content) {
    }
}
