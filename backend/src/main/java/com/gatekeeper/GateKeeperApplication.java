package com.gatekeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} backs ReportTimeoutSweepJob (Unified Engineering
 * Report Architecture, Milestone 1) - the first scheduled task in this
 * codebase.
 * <p>
 * {@link BufferingApplicationStartup} (Milestone 9: Observability, Section
 * 10 - Startup Monitoring) records how long each bean/phase of startup took,
 * exposed read-only via {@code /actuator/startup} - a standard Spring Boot
 * facility, not a custom timing mechanism. The 2048-event buffer is Spring's
 * own documented default sizing recommendation and comfortably covers this
 * application's bean count.
 */
@SpringBootApplication
@EnableScheduling
public class GateKeeperApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GateKeeperApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
    }
}
