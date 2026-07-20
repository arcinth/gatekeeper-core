package com.gatekeeper.config;

import java.util.List;
import java.util.Locale;

/**
 * Shared weak-value/minimum-length checks used by every {@code @Profile("prod")}
 * secret validator (Milestone 10: Security Hardening) - {@link JwtSecretStartupValidator},
 * {@link GitHubSecretsStartupValidator}, and {@link BootstrapAdminStartupValidator}
 * each still own their secret-specific checks (an exact-default comparison,
 * a blank check, an unset-app-id sentinel), since those genuinely differ per
 * secret; only the generic "does this look like a placeholder a real operator
 * forgot to change" check is shared, since that check is identical regardless
 * of which secret it's applied to.
 * <p>
 * A plain static utility, not a Spring bean - there is nothing here that
 * benefits from dependency injection, and every validator that uses it
 * already runs its own checks in a {@code @PostConstruct} method.
 */
final class SecretStrengthValidator {

    private static final List<String> WEAK_PATTERNS = List.of(
            "changeme", "change-me", "placeholder", "password", "admin", "test", "secret123", "default");

    private SecretStrengthValidator() {
    }

    /** Case-insensitive substring match against a small list of common placeholder words. */
    static void requireNotWeak(String settingName, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String pattern : WEAK_PATTERNS) {
            if (normalized.contains(pattern)) {
                throw new IllegalStateException(
                        "Refusing to start under the 'prod' profile: " + settingName
                                + " looks like a placeholder value (contains '" + pattern + "'). Set it to a "
                                + "unique, securely generated value before starting GateKeeper under 'prod'.");
            }
        }
    }

    static void requireMinimumLength(String settingName, String value, int minimumLength) {
        if (value != null && value.length() < minimumLength) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: " + settingName + " must be at least "
                            + minimumLength + " characters long.");
        }
    }
}
