package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.exception.GitHubApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * GitHubApiClientTest constructs GitHubApiClient directly ("new GitHubApiClient(...)"),
 * so its @Retryable annotation is inert there - Spring Retry only intercepts
 * calls to a bean managed (and proxied) by a container with @EnableRetry
 * active. This test exists specifically to prove the retry annotation itself
 * behaves as configured, which the plain unit test structurally cannot show
 * (mirrors PolicyEngineIntegrationTest's role in Milestone 3: verifying a
 * framework-provided mechanism actually fires, not re-testing our own logic).
 */
@SpringJUnitConfig(GitHubApiClientRetryTest.RetryTestConfig.class)
class GitHubApiClientRetryTest {

    @Configuration
    @EnableRetry
    static class RetryTestConfig {

        static MockRestServiceServer mockServer;

        @Bean
        RestClient.Builder restClientBuilder() {
            RestClient.Builder builder = RestClient.builder();
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }

        @Bean
        GitHubApiClient gitHubApiClient(RestClient.Builder builder) {
            return new GitHubApiClient(builder, "https://api.github.com", 300);
        }
    }

    @Autowired
    private GitHubApiClient client;

    /**
     * The context (and therefore RetryTestConfig.mockServer) is cached and
     * reused across every test method in this class by Spring's TestContext
     * framework - without resetting it, a later test's .expect() call fails
     * with "Cannot add more expectations after actual requests are made"
     * because of requests a prior test already made against the same instance.
     */
    @BeforeEach
    void resetMockServer() {
        RetryTestConfig.mockServer.reset();
    }

    @Test
    void fetchPullRequestFiles_retriesATransientFailureAndSucceedsOnATheSubsequentAttempt() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        server.expect(requestTo("https://api.github.com/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.github.com/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withSuccess(
                        "[{\"filename\":\"a.txt\",\"status\":\"modified\",\"changes\":1,\"patch\":\"+x\"}]",
                        MediaType.APPLICATION_JSON));

        List<GitHubFileChange> files = client.fetchPullRequestFiles("org/repo", 7, "token");

        assertThat(files).hasSize(1);
        server.verify();
    }

    @Test
    void fetchPullRequestFiles_givesUpAfterExhaustingTheConfiguredMaxAttempts() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        // gatekeeper.github.api.retry.max-attempts defaults to 3 when the property is absent.
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("https://api.github.com/repos/org/repo/pulls/8/files?per_page=100&page=1"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        assertThatThrownBy(() -> client.fetchPullRequestFiles("org/repo", 8, "token"))
                .isInstanceOf(GitHubApiException.class);
        server.verify();
    }

    @Test
    void fetchPullRequestFiles_doesNotRetryAPermanentFailure() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        server.expect(requestTo("https://api.github.com/repos/org/repo/pulls/9/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchPullRequestFiles("org/repo", 9, "token"))
                .isInstanceOf(GitHubApiException.class);

        // Only one expectation was set up; MockRestServiceServer.verify() would fail
        // if more than the expected one request had been made.
        server.verify();
    }
}
