package com.gatekeeper.aireviewengine.provider.anthropic;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.provider.PromptBuilder;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessage;
import com.gatekeeper.aireviewengine.provider.anthropic.dto.AnthropicMessageRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds an {@link AnthropicMessageRequest} from an {@link AIReviewContext}.
 * Now a Spring bean: AnthropicAIReviewProvider (Sprint 4 Milestone 2) is the
 * concrete AIReviewProvider that consumes it.
 * <p>
 * The system prompt instructs the model to review only the added lines
 * already scoped into each ChangedFile, and to respond with a single JSON
 * object matching {@code AIReviewResponsePayload}'s schema exactly - no
 * markdown fencing, no prose outside the JSON. AnthropicResponseParser
 * depends on the model honoring this instruction; it does not attempt to
 * strip markdown fencing itself, since that is a prompting concern, not a
 * parsing concern.
 * <p>
 * <b>Prompt versioning.</b> {@code gatekeeper.ai-review.anthropic.prompt-version}
 * selects which system prompt template to use, via
 * {@link #SYSTEM_PROMPTS_BY_VERSION}. Only "v1" exists today; a future prompt
 * iteration is added as a new map entry rather than by editing "v1" in place,
 * so a prompt regression can be rolled back by config alone. An unrecognized
 * version fails fast at construction time (application startup), matching
 * this codebase's existing fail-fast-on-bad-config convention (e.g.
 * GitHubSecretsStartupValidator) rather than surfacing as a confusing
 * NullPointerException on the first review call.
 */
@Component
public class AnthropicPromptBuilder implements PromptBuilder<AnthropicMessageRequest> {

    private static final int MAX_TOKENS = 4096;

    private static final String SYSTEM_PROMPT_V1 = """
            You are an automated code review assistant. You will be shown the \
            added lines of one or more changed files from a pull request. \
            Review only the added lines shown to you.

            Respond with a single JSON object and nothing else - no markdown \
            code fences, no prose before or after it. The JSON object must \
            match this exact schema:

            {
              "summary": "short overall narrative of the change",
              "findings": [
                {
                  "type": "SUGGESTION | POTENTIAL_BUG | STYLE | PERFORMANCE | CLARITY",
                  "confidence": "LOW | MEDIUM | HIGH",
                  "filePath": "path of the file this finding relates to",
                  "lineNumber": 1,
                  "message": "what you observed",
                  "recommendation": "how to address it"
                }
              ]
            }

            If you have no findings, return an empty findings array. Omit \
            lineNumber (use null) for file-level observations.""";

    private static final Map<String, String> SYSTEM_PROMPTS_BY_VERSION = Map.of("v1", SYSTEM_PROMPT_V1);

    private final String model;
    private final String promptVersion;
    private final String systemPrompt;

    public AnthropicPromptBuilder(
            @Value("${gatekeeper.ai-review.anthropic.model}") String model,
            @Value("${gatekeeper.ai-review.anthropic.prompt-version}") String promptVersion) {
        this.model = model;
        this.promptVersion = promptVersion;
        this.systemPrompt = SYSTEM_PROMPTS_BY_VERSION.get(promptVersion);
        if (this.systemPrompt == null) {
            throw new IllegalStateException(
                    "Unknown gatekeeper.ai-review.anthropic.prompt-version '" + promptVersion
                            + "'. Supported versions: " + SYSTEM_PROMPTS_BY_VERSION.keySet());
        }
    }

    /** Exposed so AnthropicAIReviewProvider can report this as AIReviewProvider#modelName() (Sprint 4 Milestone 3). */
    public String model() {
        return model;
    }

    /** Exposed so AnthropicAIReviewProvider can report this as AIReviewProvider#promptVersion() (Sprint 4 Milestone 3). */
    public String promptVersion() {
        return promptVersion;
    }

    @Override
    public AnthropicMessageRequest buildRequest(AIReviewContext context) {
        String userContent = buildUserContent(context);
        AnthropicMessage message = new AnthropicMessage("user", userContent);
        return new AnthropicMessageRequest(model, MAX_TOKENS, systemPrompt, List.of(message));
    }

    private String buildUserContent(AIReviewContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(context.repositoryFullName()).append('\n');
        sb.append("Pull Request #").append(context.pullRequestNumber())
                .append(": ").append(context.pullRequestTitle()).append('\n');
        sb.append("Target branch: ").append(context.targetBranch()).append('\n');
        sb.append('\n');
        sb.append(formatChangedFiles(context.changedFiles()));
        return sb.toString();
    }

    private String formatChangedFiles(List<AIReviewContext.ChangedFile> changedFiles) {
        return changedFiles.stream()
                .map(file -> "File: " + file.path() + "\n```\n" + file.content() + "\n```")
                .collect(Collectors.joining("\n\n"));
    }
}
