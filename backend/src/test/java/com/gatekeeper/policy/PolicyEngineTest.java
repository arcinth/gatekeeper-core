package com.gatekeeper.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    @Test
    void evaluate_aggregatesFindingsFromEveryRule() {
        PolicyEngine engine = new PolicyEngine(List.of(
                fixedFindingRule("RULE_A", finding("RULE_A")),
                fixedFindingRule("RULE_B", finding("RULE_B"))));

        PolicyResult result = engine.evaluate(context());

        assertThat(result.findings()).hasSize(2);
        assertThat(result.rulesEvaluated()).isEqualTo(2);
        assertThat(result.findings()).extracting(PolicyFinding::ruleId).containsExactlyInAnyOrder("RULE_A", "RULE_B");
    }

    @Test
    void evaluate_returnsEmptyResultWhenNoRulesAreRegistered() {
        PolicyEngine engine = new PolicyEngine(List.of());

        PolicyResult result = engine.evaluate(context());

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
        PolicyEngine engine = new PolicyEngine(List.of(ruleC, ruleA, ruleB));

        engine.evaluate(context());

        assertThat(List.of(ruleA.invokedAt, ruleB.invokedAt, ruleC.invokedAt))
                .isSortedAccordingTo(Integer::compareTo);
    }

    @Test
    void evaluate_isolatesAThrowingRuleWithoutAffectingOtherRules() {
        PolicyRule throwing = new PolicyRule() {
            public String id() {
                return "BROKEN_RULE";
            }

            public String description() {
                return "always throws";
            }

            public List<PolicyFinding> evaluate(PolicyContext context) {
                throw new IllegalStateException("simulated rule bug");
            }
        };
        PolicyEngine engine = new PolicyEngine(List.of(throwing, fixedFindingRule("HEALTHY_RULE", finding("HEALTHY_RULE"))));

        PolicyResult result = engine.evaluate(context());

        assertThat(result.rulesEvaluated()).isEqualTo(2);
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("HEALTHY_RULE");
    }

    @Test
    void evaluate_treatsANullReturnFromARuleAsNoFindingsRatherThanThrowing() {
        PolicyRule returnsNull = new PolicyRule() {
            public String id() {
                return "NULL_RETURNING_RULE";
            }

            public String description() {
                return "misbehaves";
            }

            public List<PolicyFinding> evaluate(PolicyContext context) {
                return null;
            }
        };
        PolicyEngine engine = new PolicyEngine(List.of(returnsNull));

        PolicyResult result = engine.evaluate(context());

        assertThat(result.findings()).isEmpty();
        assertThat(result.rulesEvaluated()).isEqualTo(1);
    }

    @Test
    void evaluate_isSafeToCallConcurrentlyFromMultipleThreadsWithDistinctContexts() throws InterruptedException {
        PolicyEngine engine = new PolicyEngine(List.of(fixedFindingRule("RULE_A", finding("RULE_A"))));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<PolicyResult>> futures = java.util.stream.IntStream.range(0, 50)
                    .mapToObj(i -> CompletableFuture.supplyAsync(
                            () -> engine.evaluate(new PolicyContext((long) i, "org/repo", List.of())), executor))
                    .toList();

            List<PolicyResult> results = futures.stream().map(CompletableFuture::join).toList();

            // Every concurrent call must see its own analysisRunId reflected back correctly -
            // any cross-thread state leakage in PolicyEngine would show up as a mismatch here.
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).analysisRunId()).isEqualTo((long) i);
                assertThat(results.get(i).findings()).hasSize(1);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private PolicyContext context() {
        return new PolicyContext(1L, "org/repo", List.of());
    }

    private PolicyFinding finding(String ruleId) {
        return new PolicyFinding(ruleId, PolicyCategory.CODE_QUALITY, PolicySeverity.LOW, "f.txt", 1, "msg", "rec");
    }

    private PolicyRule fixedFindingRule(String id, PolicyFinding finding) {
        return new PolicyRule() {
            public String id() {
                return id;
            }

            public String description() {
                return "test rule " + id;
            }

            public List<PolicyFinding> evaluate(PolicyContext context) {
                return List.of(finding);
            }
        };
    }

    private RecordingRule recordingRule(String id) {
        return new RecordingRule(id);
    }

    /** Records the global invocation sequence number it was called at, to assert ordering. */
    private static final class RecordingRule implements PolicyRule {
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

        public List<PolicyFinding> evaluate(PolicyContext context) {
            invokedAt = SEQUENCE.getAndIncrement();
            return List.of();
        }
    }
}
