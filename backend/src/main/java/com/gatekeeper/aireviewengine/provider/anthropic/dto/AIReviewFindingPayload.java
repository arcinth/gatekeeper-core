package com.gatekeeper.aireviewengine.provider.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One finding entry within {@link AIReviewResponsePayload}. type and
 * confidence are plain strings here, not the platform enums - the model's
 * output is untrusted free text until AnthropicResponseParser validates each
 * value against AIReviewFindingType/AIReviewConfidence, defensively skipping
 * (not failing the whole response for) any entry that doesn't match.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIReviewFindingPayload(
        String type,
        String confidence,
        String filePath,
        Integer lineNumber,
        String message,
        String recommendation) {
}
