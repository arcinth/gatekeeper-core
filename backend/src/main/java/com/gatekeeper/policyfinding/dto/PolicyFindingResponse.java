package com.gatekeeper.policyfinding.dto;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import java.time.Instant;

/**
 * Cross-run listing row for GET /api/v1/policy-findings (Milestone 5
 * Architecture, Section 2). Denormalizes repository/PR context onto the
 * finding itself so a flat, filterable list doesn't require a second lookup -
 * populated from PolicyFindingSpecifications' base fetch-join, which is safe
 * because analysisRun/pullRequest/repository are all to-one associations.
 */
public record PolicyFindingResponse(
        Long id,
        Long analysisRunId,
        String repositoryFullName,
        Integer pullRequestNumber,
        String commitSha,
        String ruleId,
        PolicyCategory category,
        PolicySeverity severity,
        String filePath,
        int lineNumber,
        String message,
        String recommendation,
        Instant createdAt) {

    public static PolicyFindingResponse from(PolicyFindingEntity entity) {
        return new PolicyFindingResponse(
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
