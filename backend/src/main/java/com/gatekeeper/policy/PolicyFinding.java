package com.gatekeeper.policy;

/**
 * One deterministic observation produced by a single PolicyRule. Deliberately
 * a standalone type rather than a subtype of a shared "Finding" abstraction:
 * Architecture.md's Finding hierarchy (PolicyFinding/SecurityFinding/AIFinding
 * sharing a common parent) only earns its keep once a second engine exists to
 * generalize from - introducing it now, with a single subtype, would be
 * exactly the premature abstraction SOLID warns against. Revisit this the
 * moment the Security Engine milestone begins (see the Milestone 3 writeup's
 * Future Extensibility Analysis).
 *
 * @param ruleId         the producing rule's PolicyRule#id(), for traceability
 * @param category       what kind of concern this is
 * @param severity       how urgently it needs attention
 * @param filePath       path of the file the finding was found in
 * @param lineNumber     1-based line number within that file
 * @param message        human-readable description of what was found
 * @param recommendation human-readable suggestion for resolving it
 */
public record PolicyFinding(
        String ruleId,
        PolicyCategory category,
        PolicySeverity severity,
        String filePath,
        int lineNumber,
        String message,
        String recommendation) {
}
