package com.gatekeeper.securityengine;

/**
 * What kind of security concern a SecurityRule enforces. The two
 * demonstration rules only need two values; new categories are added as new
 * rule families are written, never as a change to the Security Engine itself
 * - same extension story as com.gatekeeper.policy.PolicyCategory.
 */
public enum SecurityCategory {
    /** Credentials, API keys, tokens, or other secrets committed in plain text. */
    SECRETS_EXPOSURE,
    /** Use of a cryptographic primitive or mode known to be weak or deprecated. */
    INSECURE_CRYPTOGRAPHY
}
