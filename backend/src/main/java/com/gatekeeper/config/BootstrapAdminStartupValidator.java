package com.gatekeeper.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails startup under the prod profile if BOOTSTRAP_ADMIN_PASSWORD was never
 * overridden. The value below is a public default committed to source control
 * (see application.yml's gatekeeper.bootstrap.admin.password) - update both
 * together if the default ever changes. Mirrors JwtSecretStartupValidator's
 * exact shape (a single config value compared against its own known public
 * default): BootstrapAdminInitializer seeds a real, usable ADMINISTRATOR
 * account with this password on every first startup, so an unrotated default
 * here is a live credential granting full administrative access - not just
 * an inert misconfiguration - making it the same class of risk
 * JwtSecretStartupValidator and GitHubSecretsStartupValidator already guard
 * against for the other two secrets this project depends on.
 * Scoped to @Profile("prod") only: this bean does not exist under local/dev,
 * so it has no effect on those profiles.
 */
@Component
@Profile("prod")
public class BootstrapAdminStartupValidator {

    private static final String DEFAULT_BOOTSTRAP_ADMIN_PASSWORD = "ChangeMe123!";

    // A real login credential granting full administrative access (Milestone 10:
    // Security Hardening) - 12 characters is a common enterprise minimum-length floor.
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final String bootstrapAdminPassword;

    public BootstrapAdminStartupValidator(
            @Value("${gatekeeper.bootstrap.admin.password}") String bootstrapAdminPassword) {
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @PostConstruct
    public void validate() {
        if (DEFAULT_BOOTSTRAP_ADMIN_PASSWORD.equals(bootstrapAdminPassword)) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: BOOTSTRAP_ADMIN_PASSWORD is still set to its "
                            + "default development value. Set the BOOTSTRAP_ADMIN_PASSWORD environment variable to "
                            + "a unique, securely generated password before starting GateKeeper under 'prod'.");
        }
        SecretStrengthValidator.requireNotWeak("BOOTSTRAP_ADMIN_PASSWORD", bootstrapAdminPassword);
        SecretStrengthValidator.requireMinimumLength("BOOTSTRAP_ADMIN_PASSWORD", bootstrapAdminPassword, MINIMUM_PASSWORD_LENGTH);
    }
}
