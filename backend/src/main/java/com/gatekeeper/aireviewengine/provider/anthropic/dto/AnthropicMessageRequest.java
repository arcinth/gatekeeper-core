package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body shape for Anthropic's Messages API
 * (POST /v1/messages). No HTTP client sends this yet (Sprint 4 Milestone 1
 * scope) - this DTO exists so AnthropicPromptBuilder's output is directly
 * reusable by the concrete provider implementation a future milestone builds.
 */
public record AnthropicMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<AnthropicMessage> messages) {
}
