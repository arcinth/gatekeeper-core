package com.gatekeeper.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.analysisrun.AnalysisRun;
import org.junit.jupiter.api.Test;

class EngineeringReportMapperTest {

    @Test
    void toEntity_copiesTheAnalysisRunAndTheResolvedAiReviewStatusOntoTheEntity() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();

        EngineeringReport entity = EngineeringReportMapper.toEntity(analysisRun, AiReviewStatus.INCLUDED);

        assertThat(entity.getAnalysisRun()).isSameAs(analysisRun);
        assertThat(entity.getAiReviewStatus()).isEqualTo(AiReviewStatus.INCLUDED);
    }

    @Test
    void toEntity_toleratesEachAiReviewStatusValue() {
        AnalysisRun analysisRun = AnalysisRun.builder().build();

        assertThat(EngineeringReportMapper.toEntity(analysisRun, AiReviewStatus.UNAVAILABLE).getAiReviewStatus())
                .isEqualTo(AiReviewStatus.UNAVAILABLE);
        assertThat(EngineeringReportMapper.toEntity(analysisRun, AiReviewStatus.DISABLED).getAiReviewStatus())
                .isEqualTo(AiReviewStatus.DISABLED);
    }
}
