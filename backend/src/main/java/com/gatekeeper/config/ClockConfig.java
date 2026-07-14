package com.gatekeeper.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable Clock bean so time-dependent logic (token expiry,
 * JWT clock-skew handling) can be tested with a fixed/mutable clock instead
 * of depending on Instant.now() directly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
