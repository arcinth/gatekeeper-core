package com.gatekeeper.orchestration;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.pullrequest.PullRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Translates GitHub's changed-files response into the frozen AIReviewContext
 * shape (Sprint 4 Architecture). A near-exact duplicate of
 * PolicyContextFactory/SecurityContextFactory's added-lines extraction logic
 * rather than sharing it with them - same ADR-029 reasoning: extracting a
 * shared diff-parsing utility would require modifying those frozen classes,
 * which this milestone does not do.
 * <p>
 * Wider than PolicyContext/SecurityContext's factories: AIReviewContext also
 * carries pullRequestNumber/pullRequestTitle/targetBranch, which the
 * deterministic contexts have no equivalent for (prompt quality benefits from
 * PR intent in a way pure pattern matching never needs - see AIReviewContext's
 * own Javadoc).
 */
@Slf4j
@Component
public class AIReviewContextFactory {

    private static final String ADDED_LINE_PREFIX = "+";
    private static final String FILE_HEADER_PREFIX = "+++";

    public AIReviewContext build(AnalysisRun analysisRun, List<GitHubFileChange> changedFiles) {
        PullRequest pullRequest = analysisRun.getPullRequest();
        String repositoryFullName = pullRequest.getRepository().getFullName();

        List<AIReviewContext.ChangedFile> files = changedFiles.stream()
                .map(this::toChangedFile)
                .flatMap(Optional::stream)
                .toList();

        return new AIReviewContext(
                analysisRun.getId(),
                repositoryFullName,
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                pullRequest.getTargetBranch(),
                files);
    }

    private Optional<AIReviewContext.ChangedFile> toChangedFile(GitHubFileChange file) {
        if (file.patch() == null || file.patch().isBlank()) {
            log.debug("Skipping '{}': no patch available (binary file, rename, or too large).", file.filename());
            return Optional.empty();
        }

        String addedLines = extractAddedLines(file.patch());
        if (addedLines.isBlank()) {
            log.debug("Skipping '{}': patch contains no added lines (pure deletion).", file.filename());
            return Optional.empty();
        }

        return Optional.of(new AIReviewContext.ChangedFile(file.filename(), addedLines));
    }

    private String extractAddedLines(String patch) {
        return patch.lines()
                .filter(line -> line.startsWith(ADDED_LINE_PREFIX) && !line.startsWith(FILE_HEADER_PREFIX))
                .map(line -> line.substring(1))
                .collect(Collectors.joining("\n"));
    }
}
