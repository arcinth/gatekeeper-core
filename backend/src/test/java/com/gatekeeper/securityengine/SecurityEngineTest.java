package com.gatekeeper.securityengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SecurityEngineTest {

    @Test
    void evaluate_aggregatesFindingsFromEveryRule() {
        SecurityEngine engine = new SecurityEngine(List.of(
                fixedFindingRule("RULE_A", finding("RULE_A")),
                fixedFindingRule("RULE_B", finding("RULE_B"))));

        SecurityResult result = engine.evaluate(context());

        assertThat(result.findings()).hasSize(2);
        assertThat(result.rulesEvaluated()).isEqualTo(2);
        assertThat(result.findings()).extracting(SecurityFinding::ruleId).containsExactlyInAnyOrder("RULE_A", "RULE_B");
    }

    @Test
    void evaluate_returnsEmptyResultWhenNoRulesAreRegistered() {
        SecurityEngine engine = new SecurityEngine(List.of());

        SecurityResult result = engine.evaluate(context());

        assertThat(result.findings()).isEmpty();
        assertThat(result.rulesEvaluated()).isZero();
        assertThat(result.hasFindings()).isFalse();
    }

    @Test
    void evaluate_executesRulesInDeterministicIdOrderRegardlessOfInjectionOrder() {
        // Deliberately constructed out of alphabetical order, mirroring how
        // Spring's bean injection order is not guaranteed to match declaration order.
        RecordingRule ruleC = recordingRule("RULE_C");
        RecordingRule ruleA = recordingRule("RULE_A");
        RecordingRule ruleB = recordingRule("RULE_B");
        SecurityEngine engine = new SecurityEngine(List.of(ruleC, ruleA, ruleB));

        engine.evaluate(context());

        assertThat(List.of(ruleA.invokedAt, ruleB.invokedAt, ruleC.invokedAt))
                .isSortedAccordingTo(Integer::compareTo);
    }

    @Test
    void evaluate_isolatesAThrowingRuleWithoutAffectingOtherRules() {
        SecurityRule throwing = new SecurityRule() {
            public String id() {
                return "BROKEN_RULE";
            }

            public String description() {
                return "always throws";
            }

            public List<SecurityFinding> evaluate(SecurityContext context) {
                throw new IllegalStateException("simulated rule bug");
            }
        };
        SecurityEngine engine = new SecurityEngine(
                List.of(throwing, fixedFindingRule("HEALTHY_RULE", finding("HEALTHY_RULE"))));

        SecurityResult result = engine.evaluate(context());

        assertThat(result.rulesEvaluated()).isEqualTo(2);
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("HEALTHY_RULE");
    }

    @Test
    void evaluate_treatsANullReturnFromARuleAsNoFindingsRatherThanThrowing() {
        SecurityRule returnsNull = new SecurityRule() {
            public String id() {
                return "NULL_RETURNING_RULE";
            }

            public String description() {
                return "misbehaves";
            }

            public List<SecurityFinding> evaluate(SecurityContext context) {
                return null;
            }
        };
        SecurityEngine engine = new SecurityEngine(List.of(returnsNull));

        SecurityResult result = engine.evaluate(context());

        assertThat(result.findings()).isEmpty();
        assertThat(result.rulesEvaluated()).isEqualTo(1);
    }

    @Test
    void evaluate_isSafeToCallConcurrentlyFromMultipleThreadsWithDistinctContexts() throws InterruptedException {
        SecurityEngine engine = new SecurityEngine(List.of(fixedFindingRule("RULE_A", finding("RULE_A"))));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<SecurityResult>> futures = java.util.stream.IntStream.range(0, 50)
                    .mapToObj(i -> CompletableFuture.supplyAsync(
                            () -> engine.evaluate(new SecurityContext((long) i, "org/repo", List.of())), executor))
                    .toList();

            List<SecurityResult> results = futures.stream().map(CompletableFuture::join).toList();

            // Every concurrent call must see its own analysisRunId reflected back correctly -
            // any cross-thread state leakage in SecurityEngine would show up as a mismatch here.
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).analysisRunId()).isEqualTo((long) i);
                assertThat(results.get(i).findings()).hasSize(1);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private SecurityContext context() {
        return new SecurityContext(1L, "org/repo", List.of());
    }

    private SecurityFinding finding(String ruleId) {
        return new SecurityFinding(ruleId, SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.LOW, "f.txt", 1, "msg", "rec");
    }

    private SecurityRule fixedFindingRule(String id, SecurityFinding finding) {
        return new SecurityRule() {
            public String id() {
                return id;
            }

            public String description() {
                return "test rule " + id;
            }

            public List<SecurityFinding> evaluate(SecurityContext context) {
                return List.of(finding);
            }
        };
    }

    private RecordingRule recordingRule(String id) {
        return new RecordingRule(id);
    }

    /** Records the global invocation sequence number it was called at, to assert ordering. */
    private static final class RecordingRule implements SecurityRule {
        private static final AtomicInteger SEQUENCE = new AtomicInteger();

        private final String id;
        private volatile int invokedAt = -1;

        private RecordingRule(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String description() {
            return "recording rule " + id;
        }

        public List<SecurityFinding> evaluate(SecurityContext context) {
            invokedAt = SEQUENCE.getAndIncrement();
            return List.of();
        }
    }
}
