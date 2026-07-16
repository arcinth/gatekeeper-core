package com.gatekeeper.aireviewengine.provider.anthropic;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the single, fully-configured RestClient AnthropicAIReviewProvider
 * issues outbound calls through.
 * <p>
 * Deliberately kept out of AnthropicAIReviewProvider's own constructor:
 * {@code MockRestServiceServer.bindTo(RestClient.Builder)} works by setting a
 * mock {@code ClientHttpRequestFactory} on the builder before {@code .build()}
 * is called. If AnthropicAIReviewProvider's constructor also called
 * {@code .requestFactory(...)} on that same injected builder (needed for
 * connect/read timeout configuration), it would silently overwrite the mock
 * factory with a real one - which is exactly what happened during Milestone 2
 * verification: unit tests bound to a mock server were instead making real
 * outbound calls to api.anthropic.com and failing with a genuine 401. Building
 * the fully-configured RestClient here, once, and injecting the finished
 * object rather than the builder keeps AnthropicAIReviewProvider's own
 * constructor free of any {@code requestFactory()} call, restoring the exact
 * mockability GitHubApiClient already has (its constructor never touches the
 * request factory either).
 */
@Configuration
public class AnthropicRestClientConfig {

    @Bean
    public RestClient anthropicRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${gatekeeper.ai-review.anthropic.base-url}") String baseUrl,
            @Value("${gatekeeper.ai-review.anthropic.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${gatekeeper.ai-review.anthropic.read-timeout-ms}") int readTimeoutMs) {
        ClientHttpRequestFactorySettings requestFactorySettings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(requestFactorySettings))
                .build();
    }
}
