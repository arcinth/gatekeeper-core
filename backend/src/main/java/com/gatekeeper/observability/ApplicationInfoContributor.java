package com.gatekeeper.observability;

import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Adds Java/Spring Boot version and a human-readable deployment environment
 * label to {@code /actuator/info} (Milestone 9: Observability, Section 9).
 * Version, build time, and git commit are already supplied automatically by
 * Spring Boot's own {@code build-info}/{@code git-commit-id} plugin output
 * (see {@code pom.xml}) - this contributor adds only what those don't cover,
 * rather than duplicating them.
 * <p>
 * Deliberately does not read from {@code Environment}/{@code System.getenv()}
 * generically the way {@code /actuator/env} would - only the two specific,
 * known-safe values below are ever exposed, so this class can never become a
 * secrets-leak surface no matter how the application's configuration grows.
 */
@Component
public class ApplicationInfoContributor implements InfoContributor {

    private final String deploymentEnvironment;

    public ApplicationInfoContributor(@Value("${gatekeeper.observability.environment}") String deploymentEnvironment) {
        this.deploymentEnvironment = deploymentEnvironment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("java", Map.of(
                "version", System.getProperty("java.version"),
                "vendor", System.getProperty("java.vendor", "unknown")));
        builder.withDetail("spring-boot", Map.of("version", SpringBootVersion.getVersion()));
        builder.withDetail("deployment", Map.of("environment", describeEnvironment(deploymentEnvironment)));
    }

    /**
     * A case-insensitive substring match against {@code gatekeeper.observability.environment}
     * (an operator-supplied free string, e.g. {@code DEPLOYMENT_ENVIRONMENT=prod-us-east}) -
     * this project has no formal profile-to-environment naming contract (only a
     * "local" profile is formally distinguished today, see logback-spring.xml),
     * so a flexible heuristic is more honest than pretending one exists.
     */
    private String describeEnvironment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("prod")) {
            return "Production";
        }
        if (normalized.contains("stag")) {
            return "Staging";
        }
        if (normalized.contains("dev")) {
            return "Development";
        }
        if (normalized.contains("local")) {
            return "Local";
        }
        return raw;
    }
}
