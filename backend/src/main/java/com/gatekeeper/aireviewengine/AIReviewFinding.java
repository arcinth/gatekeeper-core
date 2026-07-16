package com.gatekeeper.aireviewengine;

/**
 * One advisory observation produced by the active AIReviewProvider. Never a
 * subtype of PolicyFinding/SecurityFinding, and never using their severity
 * vocabulary - see AIReviewConfidence's Javadoc for why that distinction is
 * load-bearing, not cosmetic.
 *
 * @param type           what kind of observation this is (platform-defined, normalized from provider output)
 * @param confidence     how confident the provider was in this observation - not a severity claim
 * @param filePath       path of the file the finding relates to
 * @param lineNumber     1-based line number within that file, nullable - an AI observation may be file-level
 * @param message        human-readable description of what was observed
 * @param recommendation human-readable suggestion for addressing it
 */
public record AIReviewFinding(
        AIReviewFindingType type,
        AIReviewConfidence confidence,
        String filePath,
        Integer lineNumber,
        String message,
        String recommendation) {
}
