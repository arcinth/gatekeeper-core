package com.gatekeeper.policy;

import java.util.List;

/**
 * Everything a PolicyRule needs to evaluate one AnalysisRun. Holds plain
 * descriptive values rather than a live AnalysisRun/Repository entity
 * reference on purpose: PolicyRule implementations should never need a JPA
 * session or the persistence module on their classpath to run, which keeps
 * rule unit tests trivial to write (construct a PolicyContext literal, no
 * database or Spring context required).
 * <p>
 * Real diff content is not wired in yet - fetching it requires extending
 * GitHubApiClient (Sprint 2 Architecture, Section 6), which is out of scope
 * for this milestone. Callers construct ChangedFiles directly for now.
 *
 * @param analysisRunId    identifies which AnalysisRun this evaluation belongs to
 * @param repositoryFullName the "org/repo" the AnalysisRun's PullRequest belongs to, for logging
 * @param changedFiles      the file contents to evaluate rules against
 */
public record PolicyContext(
        Long analysisRunId,
        String repositoryFullName,
        List<ChangedFile> changedFiles) {

    public record ChangedFile(String path, String content) {
    }
}
