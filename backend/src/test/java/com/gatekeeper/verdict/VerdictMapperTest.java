package com.gatekeeper.verdict;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.verdictengine.VerdictOutcome;
import com.gatekeeper.verdictengine.VerdictReason;
import com.gatekeeper.verdictengine.VerdictResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictMapperTest {

    @Test
    void toEntity_copiesTheOutcomeAndTheAnalysisRunOntoTheEntity() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        VerdictResult result = new VerdictResult(1L, VerdictOutcome.BLOCKED, List.of(), Instant.now());

        Verdict entity = VerdictMapper.toEntity(analysisRun, result);

        assertThat(entity.getAnalysisRun()).isSameAs(analysisRun);
        assertThat(entity.getOutcome()).isEqualTo(VerdictOutcome.BLOCKED);
    }

    @Test
    void toReasonEntity_copiesEveryFieldFromTheFrozenRecordOntoTheEntityAndLinksTheParentVerdict() {
        Verdict verdict = Verdict.builder().outcome(VerdictOutcome.BLOCKED).build();
        VerdictReason reason = new VerdictReason("CRITICAL_SECURITY_FINDING",
                "CRITICAL security finding 'HARDCODED_SECRET' in src/Config.java:1 - Possible hardcoded secret", true);

        VerdictReasonEntity entity = VerdictMapper.toReasonEntity(verdict, reason);

        assertThat(entity.getVerdict()).isSameAs(verdict);
        assertThat(entity.getRuleId()).isEqualTo("CRITICAL_SECURITY_FINDING");
        assertThat(entity.isBlocking()).isTrue();
        assertThat(entity.getMessage())
                .isEqualTo("CRITICAL security finding 'HARDCODED_SECRET' in src/Config.java:1 - Possible hardcoded secret");
    }

    @Test
    void toReasonEntity_toleratesANonBlockingReason() {
        Verdict verdict = Verdict.builder().outcome(VerdictOutcome.APPROVED).build();
        VerdictReason reason = new VerdictReason("SOME_RULE", "informational only", false);

        VerdictReasonEntity entity = VerdictMapper.toReasonEntity(verdict, reason);

        assertThat(entity.isBlocking()).isFalse();
    }
}
