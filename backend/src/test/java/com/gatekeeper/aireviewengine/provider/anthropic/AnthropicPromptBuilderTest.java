package com.gatekeeper.aireviewengine.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessage;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicPromptBuilderTest {

    private final AnthropicPromptBuilder promptBuilder = new AnthropicPromptBuilder("claude-opus-4-6", "v1");

    @Test
    void buildRequest_producesASingleUserMessageContainingPrAndFileDetails() {
        AIReviewContext context = new AIReviewContext(
                1L,
                "org/repo",
                7,
                "Add feature",
                "main",
                List.of(new AIReviewContext.ChangedFile("src/Foo.java", "+ System.out.println(\"hi\");")));

        AnthropicMessageRequest request = promptBuilder.buildRequest(context);

        assertThat(request.messages()).hasSize(1);
        AnthropicMessage message = request.messages().get(0);
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content())
                .contains("org/repo")
                .contains("Pull Request #7")
                .contains("Add feature")
                .contains("main")
                .contains("src/Foo.java")
                .contains("System.out.println(\"hi\");");
    }

    @Test
    void buildRequest_includesTheStructuredOutputSchemaInTheSystemPrompt() {
        AIReviewContext context = new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());

        AnthropicMessageRequest request = promptBuilder.buildRequest(context);

        assertThat(request.system())
                .contains("\"summary\"")
                .contains("\"findings\"")
                .contains("SUGGESTION")
                .contains("POTENTIAL_BUG")
                .contains("LOW")
                .contains("HIGH");
    }

    @Test
    void buildRequest_setsTheConfiguredModelAndAPositiveMaxTokens() {
        AIReviewContext context = new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());

        AnthropicMessageRequest request = promptBuilder.buildRequest(context);

        assertThat(request.model()).isEqualTo("claude-opus-4-6");
        assertThat(request.maxTokens()).isPositive();
    }

    @Test
    void constructor_usesADifferentModelWhenConfiguredWithOne() {
        AnthropicPromptBuilder builder = new AnthropicPromptBuilder("claude-haiku-4-5", "v1");
        AIReviewContext context = new AIReviewContext(1L, "org/repo", 7, "Add feature", "main", List.of());

        AnthropicMessageRequest request = builder.buildRequest(context);

        assertThat(request.model()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void constructor_throwsIllegalStateExceptionForAnUnrecognizedPromptVersion() {
        assertThatThrownBy(() -> new AnthropicPromptBuilder("claude-opus-4-6", "v99-does-not-exist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("v99-does-not-exist");
    }

    @Test
    void buildRequest_handlesMultipleChangedFiles() {
        AIReviewContext context = new AIReviewContext(
                1L,
                "org/repo",
                7,
                "Add feature",
                "main",
                List.of(
                        new AIReviewContext.ChangedFile("a.java", "content-a"),
                        new AIReviewContext.ChangedFile("b.java", "content-b")));

        AnthropicMessageRequest request = promptBuilder.buildRequest(context);

        String content = request.messages().get(0).content();
        assertThat(content).contains("a.java").contains("content-a");
        assertThat(content).contains("b.java").contains("content-b");
    }
}
