package com.gatekeeper.aireviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AIReviewEngineServiceTest {

    private final AIReviewEngine aiReviewEngine = mock(AIReviewEngine.class);
    private final AIReviewEngineService service = new AIReviewEngineService(aiReviewEngine);

    @Test
    void review_delegatesToTheAIReviewEngineAndReturnsItsResultUnchanged() {
        AIReviewContext context = new AIReviewContext(42L, "org/repo", 7, "Add feature", "main", List.of());
        AIReviewResult expected = new AIReviewResult(42L, "test-provider", "summary", List.of(), Instant.now());
        when(aiReviewEngine.evaluate(context)).thenReturn(expected);

        AIReviewResult result = service.review(context);

        assertThat(result).isEqualTo(expected);
        verify(aiReviewEngine).evaluate(context);
    }
}
