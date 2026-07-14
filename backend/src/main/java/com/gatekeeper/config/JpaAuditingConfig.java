package com.gatekeeper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of GateKeeperApplication so @WebMvcTest slices - which use the main
 * class as their configuration source but don't set up JPA - don't pull in the
 * JPA auditing handler and fail with "JPA metamodel must not be empty".
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
