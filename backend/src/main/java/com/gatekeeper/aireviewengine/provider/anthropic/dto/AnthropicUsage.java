package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Token usage reported by Anthropic's Messages API - not consumed by parsing logic yet, kept for future cost tracking. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicUsage(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens) {
}
