package com.gatekeeper.aireviewengine.provider.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewFinding;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.aireviewengine.provider.ResponseParser;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AIReviewFindingPayload;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AIReviewResponsePayload;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicContentBlock;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parses an {@link AnthropicMessageResponse} into a normalized
 * {@link AIReviewResult}. A Spring bean since Sprint 4 Milestone 2, consumed
 * by AnthropicAIReviewProvider. Still takes a plain {@link ObjectMapper}
 * constructor argument (Spring's auto-configured bean satisfies it) rather
 * than doing its own JSON configuration, so it stays unit-testable in
 * isolation exactly like PolicyEngine/SecurityEngine's components are.
 * <p>
 * Honors the two-tier defensive-parsing contract documented on
 * {@link ResponseParser}: a missing/empty content block or unparseable JSON
 * body throws {@link AIProviderException} and fails the whole review: a
 * single malformed finding entry (unrecognized type/confidence, or one that
 * fails to map) is skipped with a warning, and the rest of the response is
 * still used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicResponseParser implements ResponseParser<AnthropicMessageResponse> {

    /** Single source of truth for this provider's name, also used by AnthropicAIReviewProvider#providerName(). */
    public static final String PROVIDER_NAME = "anthropic-claude";

    private final ObjectMapper objectMapper;

    @Override
    public AIReviewResult parse(AIReviewContext context, AnthropicMessageResponse response) {
        String text = extractText(response);
        AIReviewResponsePayload payload = deserialize(text);

        List<AIReviewFinding> findings = payload.findings() == null
                ? List.of()
                : payload.findings().stream()
                        .map(this::toFinding)
                        .filter(Objects::nonNull)
                        .toList();

        return new AIReviewResult(
                context.analysisRunId(),
                PROVIDER_NAME,
                payload.summary(),
                findings,
                Instant.now());
    }

    private String extractText(AnthropicMessageResponse response) {
        List<AnthropicContentBlock> content = response.content();
        if (content == null || content.isEmpty()) {
            throw new AIProviderException("Anthropic response contained no content blocks.");
        }
        String text = content.get(0).text();
        if (text == null || text.isBlank()) {
            throw new AIProviderException("Anthropic response's first content block had no text.");
        }
        return text;
    }

    private AIReviewResponsePayload deserialize(String text) {
        try {
            return objectMapper.readValue(text, AIReviewResponsePayload.class);
        } catch (JsonProcessingException e) {
            throw new AIProviderException("Anthropic response text was not valid AIReviewResponsePayload JSON.", e);
        }
    }

    private AIReviewFinding toFinding(AIReviewFindingPayload payload) {
        try {
            AIReviewFindingType type = AIReviewFindingType.valueOf(payload.type());
            AIReviewConfidence confidence = AIReviewConfidence.valueOf(payload.confidence());
            return new AIReviewFinding(
                    type,
                    confidence,
                    payload.filePath(),
                    payload.lineNumber(),
                    payload.message(),
                    payload.recommendation());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Skipping AI review finding with unrecognized type '{}' or confidence '{}': {}",
                    payload.type(), payload.confidence(), e.getMessage());
            return null;
        }
    }
}
