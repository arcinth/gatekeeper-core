package com.gatekeeper.aireviewengine.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewProvider;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * AnthropicAIReviewProviderTest constructs the provider directly
 * ("new AnthropicAIReviewProvider(...)"), so its {@code @Retryable}
 * annotation is inert there - Spring Retry only intercepts calls to a bean
 * managed (and proxied) by a container with {@code @EnableRetry} active
 * (the real production wiring - see AsyncConfig). This test exists
 * specifically to prove the retry annotation itself fires as configured for
 * AI Review outages (Sprint 8 Milestone 3 - AI Engine Resiliency
 * Validation), the AI Review counterpart to GitHubApiClientRetryTest, whose
 * exact role and reasoning this mirrors. Until this milestone, only
 * GitHubApiClient's own {@code @Retryable} had this level of verification -
 * AnthropicAIReviewProvider's identically-shaped annotation had only ever
 * been exercised indirectly (single-attempt classification tests), never
 * proven to actually retry end-to-end through the framework.
 */
@SpringJUnitConfig(AnthropicAIReviewProviderRetryTest.RetryTestConfig.class)
class AnthropicAIReviewProviderRetryTest {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String VALID_RESPONSE_BODY =
            "{\"id\":\"msg_1\",\"model\":\"claude-opus-4-6\",\"role\":\"assistant\","
                    + "\"content\":[{\"type\":\"text\",\"text\":"
                    + "\"{\\\"summary\\\":\\\"looks fine\\\",\\\"findings\\\":[]}\"}],"
                    + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    @Configuration
    @EnableRetry
    static class RetryTestConfig {

        static MockRestServiceServer mockServer;

        @Bean
        RestClient.Builder restClientBuilder() {
            RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }

        @Bean
        AnthropicPromptBuilder anthropicPromptBuilder() {
            return new AnthropicPromptBuilder("claude-opus-4-6", "v1");
        }

        @Bean
        AnthropicResponseParser anthropicResponseParser() {
            return new AnthropicResponseParser(new ObjectMapper());
        }

        @Bean
        AnthropicAIReviewProvider anthropicAIReviewProvider(
                RestClient.Builder builder,
                AnthropicPromptBuilder promptBuilder,
                AnthropicResponseParser responseParser) {
            return new AnthropicAIReviewProvider(
                    builder.build(), promptBuilder, responseParser, true, "test-api-key", "2023-06-01");
        }
    }

    // Autowired by the AIReviewProvider interface, not the concrete class: AnthropicAIReviewProvider
    // implements that interface, so Spring's retry AOP wraps it in a JDK dynamic proxy implementing
    // only the interface, not the concrete type - injecting by concrete type would fail to resolve
    // against that proxy (unlike GitHubApiClientRetryTest's GitHubApiClient, which implements no
    // interface and so gets a CGLIB subclass proxy instead, injectable by its own concrete type).
    @Autowired
    private AIReviewProvider provider;

    /**
     * The context (and therefore RetryTestConfig.mockServer) is cached and
     * reused across every test method in this class by Spring's TestContext
     * framework - without resetting it, a later test's .expect() call fails
     * because of requests a prior test already made against the same
     * instance (mirrors GitHubApiClientRetryTest's identical setup).
     */
    @BeforeEach
    void resetMockServer() {
        RetryTestConfig.mockServer.reset();
    }

    @Test
    void review_retriesATransientFailureAndSucceedsOnASubsequentAttempt() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        server.expect(requestTo(BASE_URL + "/v1/messages")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withSuccess(VALID_RESPONSE_BODY, MediaType.APPLICATION_JSON));

        AIReviewResult result = provider.review(context());

        assertThat(result.summary()).isEqualTo("looks fine");
        server.verify();
    }

    @Test
    void review_retriesA529OverloadedResponseAndGivesUpAfterExhaustingTheConfiguredMaxAttempts() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        // gatekeeper.ai-review.anthropic.retry.max-attempts defaults to 3 when the property is absent.
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(BASE_URL + "/v1/messages")).andRespond(withStatus(HttpStatusCode.valueOf(529)));
        }

        assertThatThrownBy(() -> provider.review(context())).isInstanceOf(AIProviderException.class);
        server.verify();
    }

    @Test
    void review_retriesA429RateLimitAndGivesUpAfterExhaustingTheConfiguredMaxAttempts() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(BASE_URL + "/v1/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        assertThatThrownBy(() -> provider.review(context())).isInstanceOf(AIProviderException.class);
        server.verify();
    }

    @Test
    void review_doesNotRetryAPermanentFailureLikeAnInvalidApiKey() {
        MockRestServiceServer server = RetryTestConfig.mockServer;
        server.expect(requestTo(BASE_URL + "/v1/messages")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.review(context())).isInstanceOf(AIProviderException.class);

        // Only one expectation was set up; server.verify() would fail if more than
        // the expected one request had been made - proving a permanent failure is
        // never retried, only classified and surfaced immediately.
        server.verify();
    }

    private AIReviewContext context() {
        return new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());
    }
}
