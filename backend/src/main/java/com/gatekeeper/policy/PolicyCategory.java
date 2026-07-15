package com.gatekeeper.policy;

/**
 * What kind of engineering concern a PolicyRule enforces. Exists so findings
 * can be grouped/filtered without parsing rule IDs; the two demonstration
 * rules only need two values, but the type is a natural extension point -
 * new categories are added as new rule families are written, never as a
 * change to the Policy Engine itself.
 */
public enum PolicyCategory {
    /** Unresolved or incomplete work markers left in code (e.g. TODO). */
    MAINTAINABILITY,
    /** Known-defect markers or other direct quality problems (e.g. FIXME). */
    CODE_QUALITY
}
