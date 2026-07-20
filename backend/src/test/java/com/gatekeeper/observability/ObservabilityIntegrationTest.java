package com.gatekeeper.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end verification of Milestone 9's management-port trust boundary
 * against a real embedded server: the allowlisted endpoints must be reachable
 * without authentication on the management port, the endpoints deliberately
 * excluded from the allowlist must not be, and the main application port must
 * not serve Actuator at all. Requires Docker for Testcontainers, so - like
 * this project's other Testcontainers-based integration tests - it does not
 * run on machines without a Docker environment (see docs/Observability.md);
 * it exists primarily for CI. The equivalent behavior was also verified
 * manually against the real running application during development (see
 * Milestone 9 deliverables report).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ObservabilityIntegrationTest {

    private static final int MANAGEMENT_PORT = 18089;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void managementPort_servesAllowlistedActuatorEndpointsWithoutAuthentication() {
        assertThat(managementGet("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/info").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/prometheus").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/startup").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void managementPort_doesNotExposeEndpointsOutsideTheAllowlist() {
        assertThat(managementGet("/actuator/env").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/beans").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/shutdown").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/heapdump").getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void mainApplicationPort_doesNotServeActuatorAtAll() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + serverPort + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void aRequest_isEchoedBackWithTheSameCorrelationIdItSupplied() {
        // CorrelationIdFilter runs for every request reaching the main dispatcher's
        // filter chain regardless of whether a handler matches, so an unmapped path
        // still exercises it - no authenticated endpoint is needed for this check.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "observability-it-fixed-id");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + serverPort + "/no-such-path",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("observability-it-fixed-id");
    }

    private ResponseEntity<String> managementGet(String path) {
        return restTemplate.getForEntity("http://localhost:" + MANAGEMENT_PORT + path, String.class);
    }
}
