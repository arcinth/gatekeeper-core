package com.gatekeeper.securityfinding.dto;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import java.time.Instant;

/**
 * Cross-run listing row for GET /api/v1/security-findings (Security Engine
 * Architecture, Section 13 - mirrors PolicyFindingResponse exactly).
 * Denormalizes repository/PR context onto the finding itself so a flat,
 * filterable list doesn't require a second lookup - populated from
 * SecurityFindingSpecifications' base fetch-join, safe because
 * analysisRun/pullRequest/repository are all to-one associations.
 */
public record SecurityFindingResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        String ruleId,
        SecurityCategory category,
        SecuritySeverity severity,
        String filePath,
        int lineNumber,
        String message,
        String recommendation,
        Instant createdAt) {

    public static SecurityFindingResponse from(SecurityFindingEntity entity) {
        return new SecurityFindingResponse(
                entity.getId(),
                entity.getAnalysisRun().getId(),
                entity.getAnalysisRun().getPullRequest().getRepository().getFullName(),
                entity.getAnalysisRun().getPullRequest().getNumber(),
                entity.getAnalysisRun().getCommitSha(),
                entity.getRuleId(),
                entity.getCategory(),
                entity.getSeverity(),
                entity.getFilePath(),
                entity.getLineNumber(),
                entity.getMessage(),
                entity.getRecommendation(),
                entity.getCreatedAt());
    }
}
