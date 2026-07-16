package com.gatekeeper.aireviewfinding;

import com.gatekeeper.aireviewengine.AIReviewFinding;
import com.gatekeeper.aireviewrun.AIReviewRun;

/**
 * Converts the frozen AIReviewFinding record into the persistable
 * AIReviewFindingEntity. Mirrors SecurityFindingMapper/PolicyFindingMapper,
 * including their visibility precedent: public, not package-private, because
 * it is called from com.gatekeeper.orchestration.AIReviewResultPersistenceService
 * - a different package.
 */
public final class AIReviewFindingMapper {

    private AIReviewFindingMapper() {
    }

    public static AIReviewFindingEntity toEntity(AIReviewRun aiReviewRun, AIReviewFinding finding) {
        return AIReviewFindingEntity.builder()
                .aiReviewRun(aiReviewRun)
                .type(finding.type())
                .confidence(finding.confidence())
                .filePath(finding.filePath())
                .lineNumber(finding.lineNumber())
                .message(finding.message())
                .recommendation(finding.recommendation())
                .build();
    }
}
