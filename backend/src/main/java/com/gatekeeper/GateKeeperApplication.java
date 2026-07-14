package com.gatekeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GateKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateKeeperApplication.class, args);
    }
}
