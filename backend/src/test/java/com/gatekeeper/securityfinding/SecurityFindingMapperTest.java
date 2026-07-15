package com.gatekeeper.securityfinding;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import org.junit.jupiter.api.Test;

class SecurityFindingMapperTest {

    @Test
    void toEntity_copiesEveryFieldFromTheFrozenRecordOntoTheEntity() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();
        SecurityFinding finding = new SecurityFinding(
                "HARDCODED_SECRET", SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.CRITICAL,
                "src/Config.java", 42, "Possible hardcoded secret found: apiKey = \"...\"",
                "Remove the literal value and load it from a secrets manager or environment variable instead.");

        SecurityFindingEntity entity = SecurityFindingMapper.toEntity(analysisRun, finding);

        assertThat(entity.getAnalysisRun()).isSameAs(analysisRun);
        assertThat(entity.getRuleId()).isEqualTo("HARDCODED_SECRET");
        assertThat(entity.getCategory()).isEqualTo(SecurityCategory.SECRETS_EXPOSURE);
        assertThat(entity.getSeverity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(entity.getFilePath()).isEqualTo("src/Config.java");
        assertThat(entity.getLineNumber()).isEqualTo(42);
        assertThat(entity.getMessage()).isEqualTo("Possible hardcoded secret found: apiKey = \"...\"");
        assertThat(entity.getRecommendation())
                .isEqualTo("Remove the literal value and load it from a secrets manager or environment variable instead.");
    }
}
