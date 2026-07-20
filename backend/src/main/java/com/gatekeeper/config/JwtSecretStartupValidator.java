package com.gatekeeper.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails startup under the prod profile if JWT_SECRET was never overridden.
 * The value below is a public default committed to source control
 * (see application.yml's gatekeeper.security.jwt.secret) - update both
 * together if the default ever changes.
 * Scoped to @Profile("prod") only: this bean does not exist under local/dev,
 * so it has no effect on those profiles.
 */
@Component
@Profile("prod")
public class JwtSecretStartupValidator {

    private static final String DEFAULT_JWT_SECRET =
            "local-development-secret-key-change-me-in-production-please-0123456789";

    private final String jwtSecret;

    public JwtSecretStartupValidator(@Value("${gatekeeper.security.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    // HS256 requires a key of at least 256 bits (32 bytes); Keys.hmacShaKeyFor already
    // enforces this at bean-construction time in every profile (see JwtService), but this
    // makes the requirement an explicit, prod-specific policy rather than an incidental
    // side effect of that library check (Milestone 10: Security Hardening).
    private static final int MINIMUM_LENGTH = 32;

    @PostConstruct
    public void validate() {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: JWT_SECRET is still set to its default "
                            + "development value. Set the JWT_SECRET environment variable to a unique, "
                            + "securely generated secret before starting GateKeeper under 'prod'.");
        }
        SecretStrengthValidator.requireNotWeak("JWT_SECRET", jwtSecret);
        SecretStrengthValidator.requireMinimumLength("JWT_SECRET", jwtSecret, MINIMUM_LENGTH);
    }
}
