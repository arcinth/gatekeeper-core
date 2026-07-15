package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.policy.PolicyContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Translates GitHub's changed-files response into the frozen PolicyContext
 * shape (Milestone 4 Architecture, Section 2). Not part of the Policy
 * Engine itself - it's the adapter that lets a frozen, diff-unaware engine
 * receive PR-scoped content without ever being modified.
 * <p>
 * A file's content is built from its patch's added lines only ("+"-prefixed,
 * with the prefix stripped) - never the full file, never the raw patch
 * verbatim. TodoCommentRule/FixmeCommentRule do plain substring matching with
 * no concept of diff syntax; feeding them the raw patch would flag markers in
 * unchanged context lines and even in lines being deleted. Restricting to
 * added lines keeps findings scoped to what this PR actually introduces
 * (ADR-014).
 */
@Slf4j
@Component
public class PolicyContextFactory {

    private static final String ADDED_LINE_PREFIX = "+";
    private static final String FILE_HEADER_PREFIX = "+++";

    public PolicyContext build(AnalysisRun analysisRun, List<GitHubFileChange> changedFiles) {
        String repositoryFullName = analysisRun.getPullRequest().getRepository().getFullName();

        List<PolicyContext.ChangedFile> files = changedFiles.stream()
                .map(this::toChangedFile)
                .flatMap(Optional::stream)
                .toList();

        return new PolicyContext(analysisRun.getId(), repositoryFullName, files);
    }

    private Optional<PolicyContext.ChangedFile> toChangedFile(GitHubFileChange file) {
        if (file.patch() == null || file.patch().isBlank()) {
            log.debug("Skipping '{}': no patch available (binary file, rename, or too large).", file.filename());
            return Optional.empty();
        }

        String addedLines = extractAddedLines(file.patch());
        if (addedLines.isBlank()) {
            log.debug("Skipping '{}': patch contains no added lines (pure deletion).", file.filename());
            return Optional.empty();
        }

        return Optional.of(new PolicyContext.ChangedFile(file.filename(), addedLines));
    }

    private String extractAddedLines(String patch) {
        return patch.lines()
                .filter(line -> line.startsWith(ADDED_LINE_PREFIX) && !line.startsWith(FILE_HEADER_PREFIX))
                .map(line -> line.substring(1))
                .collect(Collectors.joining("\n"));
    }
}
