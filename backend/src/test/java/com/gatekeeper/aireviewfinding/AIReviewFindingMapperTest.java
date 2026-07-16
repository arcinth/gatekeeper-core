package com.gatekeeper.aireviewfinding;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFinding;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewrun.AIReviewRun;
import org.junit.jupiter.api.Test;

class AIReviewFindingMapperTest {

    @Test
    void toEntity_copiesEveryFieldFromTheFrozenRecordOntoTheEntity() {
        AIReviewRun aiReviewRun = AIReviewRun.builder().build();
        AIReviewFinding finding = new AIReviewFinding(
                AIReviewFindingType.POTENTIAL_BUG, AIReviewConfidence.HIGH,
                "src/Foo.java", 42, "possible NPE", "add a null check");

        AIReviewFindingEntity entity = AIReviewFindingMapper.toEntity(aiReviewRun, finding);

        assertThat(entity.getAiReviewRun()).isSameAs(aiReviewRun);
        assertThat(entity.getType()).isEqualTo(AIReviewFindingType.POTENTIAL_BUG);
        assertThat(entity.getConfidence()).isEqualTo(AIReviewConfidence.HIGH);
        assertThat(entity.getFilePath()).isEqualTo("src/Foo.java");
        assertThat(entity.getLineNumber()).isEqualTo(42);
        assertThat(entity.getMessage()).isEqualTo("possible NPE");
        assertThat(entity.getRecommendation()).isEqualTo("add a null check");
    }

    @Test
    void toEntity_toleratesANullLineNumberAndNullRecommendationForFileLevelObservations() {
        AIReviewRun aiReviewRun = AIReviewRun.builder().build();
        AIReviewFinding finding = new AIReviewFinding(
                AIReviewFindingType.SUGGESTION, AIReviewConfidence.LOW,
                "src/Foo.java", null, "consider splitting this file", null);

        AIReviewFindingEntity entity = AIReviewFindingMapper.toEntity(aiReviewRun, finding);

        assertThat(entity.getLineNumber()).isNull();
        assertThat(entity.getRecommendation()).isNull();
    }
}
