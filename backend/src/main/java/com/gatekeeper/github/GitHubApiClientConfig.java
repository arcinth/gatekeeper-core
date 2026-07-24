package com.gatekeeper.github;

import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Pins the {@link RestClient.Builder} {@link RestClientAutoConfiguration}
 * provides to {@link JdkClientHttpRequestFactory} (wraps
 * {@code java.net.http.HttpClient}, JDK 11+, no extra dependency) rather than
 * whatever {@code RestClient.Builder} would otherwise auto-detect from the
 * classpath. {@link GitHubApiClient#updateCheckRun} issues a PATCH request,
 * and the JDK's legacy {@code HttpURLConnection}-based factory - the
 * auto-detected fallback when nothing else on the classpath is preferred -
 * refuses PATCH outright ({@code java.net.ProtocolException: Invalid HTTP
 * method: PATCH}), silently failing every reviewer-decision check run update
 * (caught and logged by {@code GitHubReviewDecisionCheckRunPublisher}, not
 * propagated, so nothing about it was ever visible without reading logs).
 * <p>
 * A {@link RestClientCustomizer} bean, not a direct
 * {@code .requestFactory(...)} call in {@link GitHubApiClient}'s own
 * constructor: the customizer only ever touches the auto-configured builder
 * Spring hands to production code. {@code GitHubApiClientTest} and
 * {@code GitHubApiClientRetryTest} construct their own {@code RestClient.Builder}
 * by hand and bind {@code MockRestServiceServer} to it directly - a
 * constructor-level override would silently replace that mock binding with a
 * real HTTP client instead, breaking every test using it without any
 * indication why.
 */
@Configuration
public class GitHubApiClientConfig {

    @Bean
    public RestClientCustomizer jdkHttpClientRestClientCustomizer() {
        return (RestClient.Builder builder) -> builder.requestFactory(new JdkClientHttpRequestFactory());
    }
}
