package com.gatekeeper.verdict;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.verdictengine.VerdictReason;
import com.gatekeeper.verdictengine.VerdictResult;

/**
 * Converts the frozen VerdictResult/VerdictReason records into their
 * persistable entities. Mirrors SecurityFindingMapper/AIReviewFindingMapper's
 * shape: public, not package-private, because it is called from
 * com.gatekeeper.orchestration.AnalysisResultPersistenceService - a
 * different package (the same ADR-025 precedent extended a third time).
 * <p>
 * Two separate mapping methods, not one, mirroring how AIReviewResultPersistenceService
 * maps its own two-level run-then-findings shape: {@link #toEntity} produces
 * the parent Verdict (unsaved - the caller persists it to obtain a
 * generated id before mapping reasons), and {@link #toReasonEntity} maps one
 * VerdictReason at a time against that already-persisted parent, the same
 * singular-mapping-plus-caller-side-loop convention every other mapper in
 * this codebase already uses.
 */
public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static Verdict toEntity(AnalysisRun analysisRun, VerdictResult result) {
        return Verdict.builder()
                .analysisRun(analysisRun)
                .outcome(result.outcome())
                .build();
    }

    public static VerdictReasonEntity toReasonEntity(Verdict verdict, VerdictReason reason) {
        return VerdictReasonEntity.builder()
                .verdict(verdict)
                .ruleId(reason.ruleId())
                .blocking(reason.blocking())
                .message(reason.message())
                .build();
    }
}
