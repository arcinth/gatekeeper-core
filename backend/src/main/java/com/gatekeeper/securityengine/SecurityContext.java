package com.gatekeeper.securityengine;

import java.util.List;

/**
 * Everything a SecurityRule needs to evaluate one AnalysisRun. Structurally
 * identical to com.gatekeeper.policy.PolicyContext by design (Sprint 3
 * Security Engine Architecture, Section 7) but deliberately its own type, not
 * a shared/reused one - see ADR-024: each engine owns its own bounded-context
 * vocabulary rather than coupling on a type that happens to look the same today.
 * <p>
 * Holds plain descriptive values rather than a live AnalysisRun/Repository
 * entity reference, so SecurityRule implementations never need a JPA session
 * or the persistence module on their classpath to run - rule unit tests stay
 * trivial (construct a SecurityContext literal, no database or Spring context
 * required).
 * <p>
 * content is added-lines-only, the same PR-scoping convention the Policy
 * Engine already uses (ADR-014) - a security finding on a line the PR didn't
 * touch would misattribute a pre-existing issue to this PR. Not wired to real
 * diff content yet; that is orchestration-layer work for a later milestone of
 * this sprint.
 *
 * @param analysisRunId      identifies which AnalysisRun this evaluation belongs to
 * @param repositoryFullName the "org/repo" the AnalysisRun's PullRequest belongs to, for logging
 * @param changedFiles       the file contents to evaluate rules against
 */
public record SecurityContext(
        Long analysisRunId,
        String repositoryFullName,
        List<ChangedFile> changedFiles) {

    public record ChangedFile(String path, String content) {
    }
}
