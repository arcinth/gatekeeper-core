package com.gatekeeper.aireviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AIReviewEngineTest {

    @Test
    void evaluate_delegatesToTheConfiguredProviderAndReturnsItsResultUnchanged() {
        AIReviewResult expected = result(1L);
        AIReviewProvider provider = fixedResultProvider("test-provider", expected);
        AIReviewEngine engine = new AIReviewEngine(provider);

        AIReviewResult actual = engine.evaluate(context(1L));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void evaluate_propagatesProviderExceptionsUncaught() {
        AIReviewProvider throwing = new AIReviewProvider() {
            public String providerName() {
                return "broken-provider";
            }

            public String modelName() {
                return "test-model";
            }

            public String promptVersion() {
                return "v1";
            }

            public AIReviewResult review(AIReviewContext context) {
                throw new IllegalStateException("simulated provider failure");
            }
        };
        AIReviewEngine engine = new AIReviewEngine(throwing);

        assertThatThrownBy(() -> engine.evaluate(context(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated provider failure");
    }

    @Test
    void evaluate_isSafeToCallConcurrentlyFromMultipleThreadsWithDistinctContexts() throws InterruptedException {
        AIReviewProvider provider = new AIReviewProvider() {
            public String providerName() {
                return "echo-provider";
            }

            public String modelName() {
                return "test-model";
            }

            public String promptVersion() {
                return "v1";
            }

            public AIReviewResult review(AIReviewContext context) {
                return result(context.analysisRunId());
            }
        };
        AIReviewEngine engine = new AIReviewEngine(provider);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<AIReviewResult>> futures = IntStream.range(0, 50)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> engine.evaluate(context((long) i)), executor))
                    .toList();

            List<AIReviewResult> results = futures.stream().map(CompletableFuture::join).toList();

            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).analysisRunId()).isEqualTo((long) i);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private AIReviewContext context(Long analysisRunId) {
        return new AIReviewContext(analysisRunId, "org/repo", 7, "Add feature", "main", List.of());
    }

    private AIReviewResult result(Long analysisRunId) {
        return new AIReviewResult(analysisRunId, "test-provider", "summary", List.of(), Instant.now());
    }

    private AIReviewProvider fixedResultProvider(String name, AIReviewResult result) {
        return new AIReviewProvider() {
            public String providerName() {
                return name;
            }

            public String modelName() {
                return "test-model";
            }

            public String promptVersion() {
                return "v1";
            }

            public AIReviewResult review(AIReviewContext context) {
                return result;
            }
        };
    }
}
