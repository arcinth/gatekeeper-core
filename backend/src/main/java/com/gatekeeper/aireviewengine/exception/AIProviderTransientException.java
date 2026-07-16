package com.gatekeeper.aireviewengine.exception;

/**
 * An AI provider failure worth retrying: a 5xx/overloaded response, a rate
 * limit, or a network-level failure with no response at all. Kept distinct
 * from the base AIProviderException (used for permanent failures like
 * invalid API key or a malformed response) so AnthropicAIReviewProvider's
 * retry annotation can target this subtype specifically - retrying a
 * permanent failure would only waste time before failing anyway. Mirrors
 * GitHubTransientApiException's shape and reasoning exactly.
 */
public class AIProviderTransientException extends AIProviderException {

    public AIProviderTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
