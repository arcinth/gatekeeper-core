package com.gatekeeper.policy;

/**
 * How urgently a PolicyFinding needs attention. Ordered low to high; a future
 * Verdict Engine milestone can threshold on this without the Policy Engine
 * needing to know anything about verdicts (see Architecture.md's principle
 * that deterministic engines only establish findings, never decisions).
 */
public enum PolicySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
