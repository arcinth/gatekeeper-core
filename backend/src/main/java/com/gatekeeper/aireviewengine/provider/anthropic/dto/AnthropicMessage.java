package com.gatekeeper.aireviewengine.provider.anthropic.dto;

/** One entry in an Anthropic Messages API request's "messages" array. */
public record AnthropicMessage(String role, String content) {
}
