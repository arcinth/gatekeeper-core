package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry in an Anthropic Messages API response's "content" array. type is typically "text". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicContentBlock(String type, String text) {
}
