package com.gatekeeper.securityengine;

/**
 * One deterministic observation produced by a single SecurityRule. Deliberately
 * a standalone type, not a subtype of a shared "Finding" abstraction with
 * PolicyFinding - see the Sprint 3 Security Engine Architecture, ADR-022: two
 * data points still isn't enough confidence for the right shared shape,
 * especially with the future AI Review Engine's very different (advisory,
 * not deterministic) nature still to come. Revisit if a third engine arrives.
 *
 * @param ruleId         the producing rule's SecurityRule#id(), for traceability
 * @param category       what kind of security concern this is
 * @param severity       how urgently it needs attention
 * @param filePath       path of the file the finding was found in
 * @param lineNumber     1-based line number within that file
 * @param message        human-readable description of what was found
 * @param recommendation human-readable suggestion for resolving it
 */
public record SecurityFinding(
        String ruleId,
        SecurityCategory category,
        SecuritySeverity severity,
        String filePath,
        int lineNumber,
        String message,
        String recommendation) {
}
