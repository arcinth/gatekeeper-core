package com.gatekeeper.aireviewengine.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.aireviewengine.exception.AIProviderTransientException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnthropicAIReviewProviderTest {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String VALID_RESPONSE_BODY =
            "{\"id\":\"msg_1\",\"model\":\"claude-opus-4-6\",\"role\":\"assistant\","
                    + "\"content\":[{\"type\":\"text\",\"text\":"
                    + "\"{\\\"summary\\\":\\\"looks fine\\\",\\\"findings\\\":[]}\"}],"
                    + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    @Test
    void review_sendsAuthHeadersAndParsesAValidResponse() {
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-api-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(VALID_RESPONSE_BODY, MediaType.APPLICATION_JSON));

        AIReviewResult result = fixture.provider.review(context());

        assertThat(result.provider()).isEqualTo("anthropic-claude");
        assertThat(result.summary()).isEqualTo("looks fine");
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void providerName_returnsTheSharedProviderNameConstant() {
        Fixture fixture = fixture(true, "test-api-key");

        assertThat(fixture.provider.providerName()).isEqualTo("anthropic-claude");
    }

    @Test
    void modelNameAndPromptVersion_delegateToTheConfiguredPromptBuilderAndAreAvailableRegardlessOfEnabledState() {
        Fixture fixture = fixture(false, "test-api-key");

        assertThat(fixture.provider.modelName()).isEqualTo("claude-opus-4-6");
        assertThat(fixture.provider.promptVersion()).isEqualTo("v1");
    }

    @Test
    void review_throwsAIProviderExceptionWithoutCallingTheApiWhenDisabled() {
        Fixture fixture = fixture(false, "test-api-key");

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderException.class)
                .isNotInstanceOf(AIProviderTransientException.class)
                .hasMessageContaining("disabled");
        fixture.mockServer.verify();
    }

    @Test
    void review_throwsAIProviderExceptionWithoutCallingTheApiWhenApiKeyIsBlank() {
        Fixture fixture = fixture(true, "");

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderException.class)
                .isNotInstanceOf(AIProviderTransientException.class)
                .hasMessageContaining("api-key");
        fixture.mockServer.verify();
    }

    @Test
    void isAvailable_isTrueOnlyWhenEnabledAndApiKeyIsPresent() {
        assertThat(fixture(true, "key").provider.isAvailable()).isTrue();
        assertThat(fixture(false, "key").provider.isAvailable()).isFalse();
        assertThat(fixture(true, "").provider.isAvailable()).isFalse();
        assertThat(fixture(true, "   ").provider.isAvailable()).isFalse();
    }

    @Test
    void review_wrapsA5xxResponseAsTransient() {
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderTransientException.class);
    }

    @Test
    void review_wrapsA529OverloadedResponseAsTransient() {
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withStatus(HttpStatusCode.valueOf(529)));

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderTransientException.class);
    }

    @Test
    void review_wrapsA429ResponseAsTransient() {
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderTransientException.class);
    }

    @Test
    void review_wrapsA401ResponseAsPermanentNotTransient() {
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderException.class)
                .isNotInstanceOf(AIProviderTransientException.class);
    }

    @Test
    void review_wrapsA403ResponseAsPermanentNotTransient() {
        // Anthropic uses 403 exclusively for permission_error, unlike GitHub's dual use of
        // 403 for both rate limiting and real permission errors - retrying it here would
        // only waste attempts, so it must NOT be classified as transient.
        Fixture fixture = fixture(true, "test-api-key");
        fixture.mockServer.expect(requestTo(BASE_URL + "/v1/messages"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> fixture.provider.review(context()))
                .isInstanceOf(AIProviderException.class)
                .isNotInstanceOf(AIProviderTransientException.class);
    }

    private AIReviewContext context() {
        return new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());
    }

    private Fixture fixture(boolean enabled, String apiKey) {
        // Bind the mock server to the builder, then finish building the RestClient here in the
        // test - mirroring AnthropicRestClientConfig's split, so the provider's own constructor
        // never touches requestFactory() and can't clobber the mock (see AnthropicRestClientConfig's
        // Javadoc for why that clobbering is a real failure mode, not a hypothetical one).
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        AnthropicPromptBuilder promptBuilder = new AnthropicPromptBuilder("claude-opus-4-6", "v1");
        AnthropicResponseParser responseParser = new AnthropicResponseParser(new ObjectMapper());
        AnthropicAIReviewProvider provider = new AnthropicAIReviewProvider(
                restClient, promptBuilder, responseParser, enabled, apiKey, "2023-06-01");
        return new Fixture(provider, mockServer);
    }

    private record Fixture(AnthropicAIReviewProvider provider, MockRestServiceServer mockServer) {
    }
}
