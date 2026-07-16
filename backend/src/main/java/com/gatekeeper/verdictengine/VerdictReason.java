package com.gatekeeper.verdictengine;

/**
 * One VerdictRule's contribution to the decision - the unit of explainability
 * (Sprint 5 Architecture, Section 9). ruleId ties this back to the specific
 * VerdictRule#id() that produced it, mirroring how SecurityFinding#ruleId()
 * already ties a finding to its producing SecurityRule.
 * <p>
 * Deliberately no reference to a specific PolicyFinding/SecurityFinding row
 * (ADR-040): a VerdictReason is a self-contained, human-readable explanation,
 * not a join target. Full drill-down is reconstructable via the existing
 * Policy/Security findings endpoints filtered by analysisRunId.
 *
 * @param ruleId   the producing VerdictRule's id(), for traceability
 * @param message  a human-readable, self-contained explanation - the reader
 *                 should never need to cross-reference source code to
 *                 understand why a verdict landed where it did
 * @param blocking whether this reason contributes to a BLOCKED outcome;
 *                 VerdictEngine's outcome derivation reads this field
 *                 directly (see its own Javadoc) - a non-blocking reason is
 *                 informational context, not a vote
 */
public record VerdictReason(String ruleId, String message, boolean blocking) {
}
