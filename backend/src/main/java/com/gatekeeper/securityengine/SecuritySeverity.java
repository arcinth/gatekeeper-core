package com.gatekeeper.securityengine;

/**
 * How urgently a SecurityFinding needs attention. Deliberately not
 * com.gatekeeper.policy.PolicySeverity despite the identical value set today -
 * see the Sprint 3 Security Engine Architecture, ADR-024: reusing Policy's
 * enum would couple two independent engines' bounded contexts on a vocabulary
 * that may need to diverge (e.g. a security-specific tier) later.
 */
public enum SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
