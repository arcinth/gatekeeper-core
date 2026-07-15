package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.securityengine.SecurityContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Translates GitHub's changed-files response into the frozen SecurityContext
 * shape (Security Engine Architecture, Section 7). Deliberately a near-exact
 * duplicate of PolicyContextFactory rather than sharing extraction logic with
 * it - see ADR-029: extracting a shared diff-parsing utility would require
 * modifying the frozen PolicyContextFactory, which this milestone does not do.
 * <p>
 * A file's content is built from its patch's added lines only ("+"-prefixed,
 * with the prefix stripped) - the same PR-scoping convention as ADR-014: a
 * security finding on a line the PR didn't touch would misattribute a
 * pre-existing issue to this PR.
 */
@Slf4j
@Component
public class SecurityContextFactory {

    private static final String ADDED_LINE_PREFIX = "+";
    private static final String FILE_HEADER_PREFIX = "+++";

    public SecurityContext build(AnalysisRun analysisRun, List<GitHubFileChange> changedFiles) {
        String repositoryFullName = analysisRun.getPullRequest().getRepository().getFullName();

        List<SecurityContext.ChangedFile> files = changedFiles.stream()
                .map(this::toChangedFile)
                .flatMap(Optional::stream)
                .toList();

        return new SecurityContext(analysisRun.getId(), repositoryFullName, files);
    }

    private Optional<SecurityContext.ChangedFile> toChangedFile(GitHubFileChange file) {
        if (file.patch() == null || file.patch().isBlank()) {
            log.debug("Skipping '{}': no patch available (binary file, rename, or too large).", file.filename());
            return Optional.empty();
        }

        String addedLines = extractAddedLines(file.patch());
        if (addedLines.isBlank()) {
            log.debug("Skipping '{}': patch contains no added lines (pure deletion).", file.filename());
            return Optional.empty();
        }

        return Optional.of(new SecurityContext.ChangedFile(file.filename(), addedLines));
    }

    private String extractAddedLines(String patch) {
        return patch.lines()
                .filter(line -> line.startsWith(ADDED_LINE_PREFIX) && !line.startsWith(FILE_HEADER_PREFIX))
                .map(line -> line.substring(1))
                .collect(Collectors.joining("\n"));
    }
}
