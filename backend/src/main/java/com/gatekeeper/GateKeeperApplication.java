package com.gatekeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} backs ReportTimeoutSweepJob (Unified Engineering
 * Report Architecture, Milestone 1) - the first scheduled task in this
 * codebase.
 */
@SpringBootApplication
@EnableScheduling
public class GateKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateKeeperApplication.class, args);
    }
}
