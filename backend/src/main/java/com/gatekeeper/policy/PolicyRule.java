package com.gatekeeper.policy;

import java.util.List;

/**
 * The extension point future policy rules plug into. This is the entire
 * contract PolicyEngine depends on - it has no knowledge of any concrete
 * rule, so adding a new rule is exclusively a matter of writing a new
 * @Component implementing this interface, never a change to PolicyEngine
 * itself (Open/Closed Principle in the literal sense).
 * <p>
 * Implementations must be stateless and thread-safe: a single PolicyRule
 * bean is shared across every concurrent PolicyEngine#evaluate() call
 * (Spring beans are singletons by default), so instance fields must either
 * not exist or be immutable (e.g. a precompiled Pattern constant).
 */
public interface PolicyRule {

    /**
     * A short, stable, unique identifier (e.g. "TODO_COMMENT"). Used for
     * traceability on PolicyFinding#ruleId() and to make PolicyEngine's
     * execution order deterministic - never derive it from the class name
     * or bean name, since either could change without the ID needing to.
     */
    String id();

    /** A human-readable summary of what this rule checks for. */
    String description();

    /**
     * Evaluates this rule against the given context. Must return an empty
     * list (never null) when nothing is found. Must not throw for
     * expected "nothing to report" cases - PolicyEngine treats a thrown
     * exception as a rule failure, not as "no findings", and isolates it
     * from other rules rather than silently swallowing a real bug.
     */
    List<PolicyFinding> evaluate(PolicyContext context);
}
