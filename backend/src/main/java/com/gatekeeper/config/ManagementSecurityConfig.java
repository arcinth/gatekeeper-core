package com.gatekeeper.config;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Explicitly permits every request on the management port (Milestone 9:
 * Observability). Without this, Spring Boot's own
 * {@code ManagementWebSecurityAutoConfiguration} applies a *different*
 * default the moment {@code management.server.port} differs from
 * {@code server.port}: only {@code /actuator/health} is reachable
 * unauthenticated, and {@code info}/{@code metrics}/{@code prometheus} all
 * 401 - verified live against this exact configuration before writing this
 * class. That default doesn't match this project's chosen security model:
 * the management port's protection is network isolation (not published
 * publicly - see {@code docker-compose.yml} and docs/Observability.md), the
 * same trust boundary Postgres itself already relies on in this project, not
 * a second authentication scheme layered on top of it.
 * <p>
 * {@code @ManagementContextConfiguration(CHILD)} is the only way to register
 * a {@code SecurityFilterChain} that actually applies to the management
 * port's own child {@code WebApplicationContext} - a plain {@code @Bean} in
 * the main {@link SecurityConfig} lives in the parent context and is not
 * visible to {@code ManagementWebSecurityAutoConfiguration}'s
 * {@code @ConditionalOnMissingBean} check, which is scoped to the child
 * context only. Registered via
 * {@code META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports}.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
public class ManagementSecurityConfig {

    @Bean
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
