package com.gatekeeper.policyfinding;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import org.junit.jupiter.api.Test;

class PolicyFindingMapperTest {

    @Test
    void toEntity_copiesEveryFieldFromTheFrozenRecordOntoTheEntity() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        PolicyFinding finding = new PolicyFinding(
                "TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                "src/Foo.java", 42, "TODO comment found: refactor this", "Resolve or track it");

        PolicyFindingEntity entity = PolicyFindingMapper.toEntity(analysisRun, finding);

        assertThat(entity.getAnalysisRun()).isSameAs(analysisRun);
        assertThat(entity.getRuleId()).isEqualTo("TODO_COMMENT");
        assertThat(entity.getCategory()).isEqualTo(PolicyCategory.MAINTAINABILITY);
        assertThat(entity.getSeverity()).isEqualTo(PolicySeverity.LOW);
        assertThat(entity.getFilePath()).isEqualTo("src/Foo.java");
        assertThat(entity.getLineNumber()).isEqualTo(42);
        assertThat(entity.getMessage()).isEqualTo("TODO comment found: refactor this");
        assertThat(entity.getRecommendation()).isEqualTo("Resolve or track it");
    }
}
