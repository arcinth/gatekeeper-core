package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.reviewdecision.ReviewDecisionType;
import org.junit.jupiter.api.Test;

class ReviewDecisionConclusionMapperTest {

    private final ReviewDecisionConclusionMapper mapper = new ReviewDecisionConclusionMapper();

    @Test
    void toConclusion_mapsApprovedToSuccess() {
        assertThat(mapper.toConclusion(ReviewDecisionType.APPROVED)).isEqualTo("success");
    }

    @Test
    void toConclusion_mapsRejectedToFailure() {
        assertThat(mapper.toConclusion(ReviewDecisionType.REJECTED)).isEqualTo("failure");
    }
}
