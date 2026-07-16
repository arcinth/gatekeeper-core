package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response body shape for Anthropic's Messages API. Not deserialized from a real HTTP call yet (Sprint 4 Milestone 1 scope). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessageResponse(
        String id,
        String model,
        String role,
        List<AnthropicContentBlock> content,
        @JsonProperty("stop_reason") String stopReason,
        AnthropicUsage usage) {
}
