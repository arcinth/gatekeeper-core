package com.gatekeeper.aireviewengine.provider.anthropic;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewProvider;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.aireviewengine.exception.AIProviderTransientException;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageRequest;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only class permitted to issue outbound HTTP calls to the Anthropic
 * Messages API - the concrete enforcement point for that isolation, exactly
 * as GitHubApiClient is for GitHub (Sprint 4 Architecture, Section 11).
 * <p>
 * <b>Feature flag.</b> {@code gatekeeper.ai-review.enabled} is checked first,
 * before any request is built or sent: when false, {@link #review} throws
 * {@link AIProviderException} immediately. This keeps "disabled" and "the
 * provider failed" indistinguishable to callers - both are just an
 * AIProviderException - so the future orchestration layer (not built this
 * milestone) needs exactly one failure-handling path, not a special case for
 * "turned off." Deliberately checked inside {@code review()} rather than by
 * conditionally registering this bean at all: AIReviewEngine's contract is
 * "depend on exactly one AIReviewProvider," and conditional bean creation
 * would force a future caller to handle "no provider bean exists" as a
 * separate case from "the provider is disabled."
 * <p>
 * <b>Retry policy.</b> {@code @Retryable} targets {@link AIProviderTransientException}
 * only (5xx/overloaded responses, 429 rate limits, network-level failures) -
 * a permanent failure (bad API key, malformed request) fails immediately
 * rather than wasting attempts on something a retry can't fix. Mirrors
 * GitHubApiClient#fetchPullRequestFiles's exact reasoning. Must be annotated
 * on this public method, not a private helper: Spring AOP retry proxies only
 * intercept calls that arrive through the proxy, not internal self-invocation.
 * <p>
 * <b>Health reporting.</b> {@link #isAvailable()} is a configuration-only
 * readiness check (feature flag on, API key present) - it does not probe
 * Anthropic's API, so it can't report "Anthropic is currently down," only
 * "this provider is configured to be usable." Wiring that into an HTTP health
 * endpoint is explicitly out of this milestone's scope (no REST APIs); the
 * method exists so a future caller can decide whether to attempt a review at
 * all, and so operators reading logs can see *why* every review failed if
 * this provider is simply unconfigured.
 * <p>
 * <b>Timeouts.</b> Connect/read timeout configuration lives in
 * {@link AnthropicRestClientConfig}, not here - see its Javadoc for why that
 * split is deliberate and not just an arbitrary extra class.
 */
@Slf4j
@Component
public class AnthropicAIReviewProvider implements AIReviewProvider {

    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String ANTHROPIC_VERSION_HEADER = "anthropic-version";
    private static final String API_KEY_HEADER = "x-api-key";

    private final AnthropicPromptBuilder promptBuilder;
    private final AnthropicResponseParser responseParser;
    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String apiVersion;

    public AnthropicAIReviewProvider(
            RestClient anthropicRestClient,
            AnthropicPromptBuilder promptBuilder,
            AnthropicResponseParser responseParser,
            @Value("${gatekeeper.ai-review.enabled}") boolean enabled,
            @Value("${gatekeeper.ai-review.anthropic.api-key}") String apiKey,
            @Value("${gatekeeper.ai-review.anthropic.api-version}") String apiVersion) {
        this.restClient = anthropicRestClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
    }

    @Override
    public String providerName() {
        return AnthropicResponseParser.PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        return promptBuilder.model();
    }

    @Override
    public String promptVersion() {
        return promptBuilder.promptVersion();
    }

    /**
     * Configuration-only readiness check - see class Javadoc for what this
     * does and does not verify.
     */
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Retryable(
            retryFor = AIProviderTransientException.class,
            maxAttemptsExpression = "${gatekeeper.ai-review.anthropic.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${gatekeeper.ai-review.anthropic.retry.initial-backoff-ms:500}",
                    multiplierExpression = "${gatekeeper.ai-review.anthropic.retry.backoff-multiplier:2}"))
    @Override
    public AIReviewResult review(AIReviewContext context) {
        if (!enabled) {
            throw new AIProviderException(
                    "AI Review is disabled via configuration (gatekeeper.ai-review.enabled=false).");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AIProviderException("gatekeeper.ai-review.anthropic.api-key is not configured.");
        }

        AnthropicMessageRequest request = promptBuilder.buildRequest(context);
        AnthropicMessageResponse response = callAnthropic(request, context.analysisRunId());
        return responseParser.parse(context, response);
    }

    private AnthropicMessageResponse callAnthropic(AnthropicMessageRequest request, Long analysisRunId) {
        try {
            AnthropicMessageResponse response = restClient.post()
                    .uri(MESSAGES_PATH)
                    .header(API_KEY_HEADER, apiKey)
                    .header(ANTHROPIC_VERSION_HEADER, apiVersion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
            if (response == null) {
                throw new AIProviderException(
                        "Anthropic API returned an empty response body for analysis run " + analysisRunId + ".");
            }
            return response;
        } catch (RestClientResponseException ex) {
            String message = "Anthropic API returned an error for analysis run " + analysisRunId
                    + " (HTTP " + ex.getStatusCode().value() + ").";
            log.warn(message);
            if (isTransient(ex.getStatusCode())) {
                throw new AIProviderTransientException(message, ex);
            }
            throw new AIProviderException(message, ex);
        } catch (RestClientException ex) {
            String message = "Failed to reach the Anthropic API for analysis run " + analysisRunId + ".";
            log.warn(message);
            throw new AIProviderTransientException(message, ex);
        }
    }

    /**
     * 5xx covers Anthropic's overloaded_error (529) automatically, since
     * HttpStatusCode#is5xxServerError only checks the status value's
     * hundreds digit. 429 is Anthropic's rate_limit_error. Unlike
     * GitHubApiClient, 403 is NOT treated as transient here: Anthropic uses
     * 403 exclusively for permission_error (not rate limiting), so retrying
     * it would only waste attempts on a failure a retry cannot fix.
     */
    private boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }
}
