package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GateKeeper's own structured-output schema - not Anthropic's API shape, but
 * the JSON we instruct the model to produce as the text content of its
 * response (AnthropicPromptBuilder's system prompt documents this exact
 * schema). camelCase field names are deserialized directly with no
 * {@code @JsonProperty} overrides needed, since we control this schema
 * entirely and Jackson's default naming strategy already matches it -
 * consistent with GitHubFileChange's own convention.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIReviewResponsePayload(String summary, List<AIReviewFindingPayload> findings) {
}
