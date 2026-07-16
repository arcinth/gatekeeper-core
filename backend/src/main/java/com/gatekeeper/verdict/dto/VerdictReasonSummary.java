package com.gatekeeper.verdict.dto;

import com.gatekeeper.verdict.VerdictReasonEntity;
import java.time.Instant;

/**
 * The one canonical reason-DTO shape (Sprint 5 Architecture, Section 14) -
 * used both as VerdictDetailResponse's own reasons list and, unchanged, as
 * AnalysisRunDetailResponse's embedded {@code verdictReasons} field. Defined
 * once and reused rather than duplicated: the architecture names this exact
 * type for both call sites, so it is one canonical shape by design, not two
 * coincidentally-identical records - the one deliberate exception to this
 * codebase's usual duplication-over-shared-DTO convention (see
 * SecurityFindingResponse's own Javadoc for that convention's normal case).
 */
public record VerdictReasonSummary(Long id, String ruleId, String message, boolean blocking, Instant createdAt) {

    public static VerdictReasonSummary from(VerdictReasonEntity entity) {
        return new VerdictReasonSummary(
                entity.getId(), entity.getRuleId(), entity.getMessage(), entity.isBlocking(), entity.getCreatedAt());
    }
}
