package com.gatekeeper.aireviewengine.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewFinding;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicContentBlock;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicResponseParserTest {

    private final AnthropicResponseParser parser = new AnthropicResponseParser(new ObjectMapper());

    @Test
    void parse_mapsAValidResponseIntoANormalizedAIReviewResult() {
        String json = """
                {
                  "summary": "Looks reasonable overall.",
                  "findings": [
                    {
                      "type": "POTENTIAL_BUG",
                      "confidence": "HIGH",
                      "filePath": "src/Foo.java",
                      "lineNumber": 12,
                      "message": "possible NPE",
                      "recommendation": "add a null check"
                    }
                  ]
                }
                """;

        AIReviewResult result = parser.parse(context(), response(json));

        assertThat(result.analysisRunId()).isEqualTo(1L);
        assertThat(result.provider()).isEqualTo("anthropic-claude");
        assertThat(result.summary()).isEqualTo("Looks reasonable overall.");
        assertThat(result.findings()).containsExactly(new AIReviewFinding(
                AIReviewFindingType.POTENTIAL_BUG, AIReviewConfidence.HIGH, "src/Foo.java", 12, "possible NPE", "add a null check"));
    }

    @Test
    void parse_returnsEmptyFindingsWhenTheFindingsArrayIsEmpty() {
        String json = "{\"summary\": \"Nothing to report.\", \"findings\": []}";

        AIReviewResult result = parser.parse(context(), response(json));

        assertThat(result.findings()).isEmpty();
    }

    @Test
    void parse_throwsAIProviderExceptionWhenTheWholeResponseIsUnparseableJson() {
        AnthropicMessageResponse malformed = response("not valid json at all {{{");

        assertThatThrownBy(() -> parser.parse(context(), malformed))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    void parse_throwsAIProviderExceptionWhenContentIsMissing() {
        AnthropicMessageResponse noContent = new AnthropicMessageResponse("id", "model", "assistant", List.of(), "end_turn", null);

        assertThatThrownBy(() -> parser.parse(context(), noContent))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    void parse_throwsAIProviderExceptionWhenContentTextIsBlank() {
        AnthropicMessageResponse blankText = response("   ");

        assertThatThrownBy(() -> parser.parse(context(), blankText))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    void parse_skipsASingleMalformedFindingButKeepsTheRestOfTheResponse() {
        String json = """
                {
                  "summary": "Mixed batch.",
                  "findings": [
                    {
                      "type": "NOT_A_REAL_TYPE",
                      "confidence": "HIGH",
                      "filePath": "src/Bad.java",
                      "lineNumber": 1,
                      "message": "bad",
                      "recommendation": "n/a"
                    },
                    {
                      "type": "STYLE",
                      "confidence": "LOW",
                      "filePath": "src/Good.java",
                      "lineNumber": 2,
                      "message": "minor style nit",
                      "recommendation": "rename variable"
                    }
                  ]
                }
                """;

        AIReviewResult result = parser.parse(context(), response(json));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).filePath()).isEqualTo("src/Good.java");
    }

    @Test
    void parse_skipsAFindingWithAnUnrecognizedConfidenceValue() {
        String json = """
                {
                  "summary": "s",
                  "findings": [
                    {
                      "type": "STYLE",
                      "confidence": "SUPER_DUPER_SURE",
                      "filePath": "src/Bad.java",
                      "lineNumber": 1,
                      "message": "bad",
                      "recommendation": "n/a"
                    }
                  ]
                }
                """;

        AIReviewResult result = parser.parse(context(), response(json));

        assertThat(result.findings()).isEmpty();
    }

    private AIReviewContext context() {
        return new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());
    }

    private AnthropicMessageResponse response(String text) {
        AnthropicContentBlock block = new AnthropicContentBlock("text", text);
        return new AnthropicMessageResponse("id", "model", "assistant", List.of(block), "end_turn", null);
    }
}
