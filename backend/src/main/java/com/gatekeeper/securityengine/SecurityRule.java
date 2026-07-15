package com.gatekeeper.securityengine;

import java.util.List;

/**
 * The extension point future security rules plug into. This is the entire
 * contract SecurityEngine depends on - it has no knowledge of any concrete
 * rule, so adding a new rule is exclusively a matter of writing a new
 * @Component implementing this interface, never a change to SecurityEngine
 * itself (Open/Closed Principle in the literal sense) - identical extension
 * story to com.gatekeeper.policy.PolicyRule.
 * <p>
 * Implementations must be stateless and thread-safe: a single SecurityRule
 * bean is shared across every concurrent SecurityEngine#evaluate() call
 * (Spring beans are singletons by default), so instance fields must either
 * not exist or be immutable (e.g. a precompiled Pattern constant).
 */
public interface SecurityRule {

    /**
     * A short, stable, unique identifier (e.g. "HARDCODED_SECRET"). Used for
     * traceability on SecurityFinding#ruleId() and to make SecurityEngine's
     * execution order deterministic - never derive it from the class name
     * or bean name, since either could change without the ID needing to.
     */
    String id();

    /** A human-readable summary of what this rule checks for. */
    String description();

    /**
     * Evaluates this rule against the given context. Must return an empty
     * list (never null) when nothing is found. Must not throw for
     * expected "nothing to report" cases - SecurityEngine treats a thrown
     * exception as a rule failure, not as "no findings", and isolates it
     * from other rules rather than silently swallowing a real bug.
     */
    List<SecurityFinding> evaluate(SecurityContext context);
}
