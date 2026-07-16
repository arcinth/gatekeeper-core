package com.gatekeeper.verdictengine;

import java.util.List;

/**
 * The extension point future verdict rules plug into. This is the entire
 * contract VerdictEngine depends on - it has no knowledge of any concrete
 * rule, so adding a new rule (including a future organization-specific
 * threshold rule, Sprint 5 Architecture Section 18) is exclusively a matter
 * of writing a new {@code @Component} implementing this interface, never a
 * change to VerdictEngine itself (Open/Closed Principle, identical extension
 * story to PolicyRule/SecurityRule - ADR-036).
 * <p>
 * Implementations must be stateless and thread-safe: a single VerdictRule
 * bean is shared across every concurrent VerdictEngine#evaluate() call
 * (Spring beans are singletons by default), so instance fields must either
 * not exist or be immutable.
 */
public interface VerdictRule {

    /**
     * A short, stable, unique identifier (e.g. "CRITICAL_SECURITY_FINDING").
     * Used for traceability on VerdictReason#ruleId() and to make
     * VerdictEngine's execution order deterministic - never derive it from
     * the class name or bean name, since either could change without the ID
     * needing to.
     */
    String id();

    /** A human-readable summary of what this rule checks for. */
    String description();

    /**
     * Evaluates this rule against the given context. Must return an empty
     * list (never null) when the rule has nothing to report - not every
     * evaluation produces a reason, blocking or otherwise. Must not throw
     * for expected "nothing to report" cases - VerdictEngine treats a thrown
     * exception as a rule failure, not as "no reasons", and isolates it from
     * other rules rather than silently swallowing a real bug.
     */
    List<VerdictReason> evaluate(VerdictContext context);
}
