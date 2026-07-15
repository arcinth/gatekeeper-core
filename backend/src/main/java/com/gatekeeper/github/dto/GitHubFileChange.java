package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry from GitHub's GET /repos/{owner}/{repo}/pulls/{number}/files
 * response. patch is the unified-diff hunk for this file and is nullable -
 * GitHub omits it for binary files, pure renames, and files it considers too
 * large. Callers must treat a null patch as "nothing to scan," not an error
 * (Milestone 4 Architecture, Section 1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubFileChange(
        String filename,
        String status,
        Integer changes,
        String patch) {
}
