package com.gatekeeper.verdictengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VerdictEngineTest {

    @Test
    void evaluate_aggregatesReasonsFromEveryRule() {
        VerdictEngine engine = new VerdictEngine(List.of(
                fixedReasonRule("RULE_A", reason("RULE_A", false)),
                fixedReasonRule("RULE_B", reason("RULE_B", false))));

        VerdictResult result = engine.evaluate(context());

        assertThat(result.reasons()).hasSize(2);
        assertThat(result.reasons()).extracting(VerdictReason::ruleId).containsExactlyInAnyOrder("RULE_A", "RULE_B");
    }

    @Test
    void evaluate_returnsApprovedWithNoReasonsWhenNoRulesAreRegistered() {
        VerdictEngine engine = new VerdictEngine(List.of());

        VerdictResult result = engine.evaluate(context());

        assertThat(result.reasons()).isEmpty();
        assertThat(result.outcome()).isEqualTo(VerdictOutcome.APPROVED);
    }

    @Test
    void evaluate_returnsApprovedWhenEveryReasonIsNonBlocking() {
        VerdictEngine engine = new VerdictEngine(List.of(
                fixedReasonRule("RULE_A", reason("RULE_A", false)),
                fixedReasonRule("RULE_B", reason("RULE_B", false))));

        VerdictResult result = engine.evaluate(context());

        assertThat(result.outcome()).isEqualTo(VerdictOutcome.APPROVED);
    }

    @Test
    void evaluate_returnsBlockedWhenAnySingleReasonIsBlockingEvenAmongMostlyNonBlockingReasons() {
        VerdictEngine engine = new VerdictEngine(List.of(
                fixedReasonRule("RULE_A", reason("RULE_A", false)),
                fixedReasonRule("RULE_B", reason("RULE_B", true)),
                fixedReasonRule("RULE_C", reason("RULE_C", false))));

        VerdictResult result = engine.evaluate(context());

        assertThat(result.outcome()).isEqualTo(VerdictOutcome.BLOCKED);
        // A BLOCKED outcome still retains every reason, not just the blocking one(s) -
        // the audit trail must show everything that was checked.
        assertThat(result.reasons()).hasSize(3);
    }

    @Test
    void evaluate_carriesTheAnalysisRunIdFromTheContext() {
        VerdictEngine engine = new VerdictEngine(List.of());

        VerdictResult result = engine.evaluate(context());

        assertThat(result.analysisRunId()).isEqualTo(1L);
    }

    @Test
    void evaluate_executesRulesInDeterministicIdOrderRegardlessOfInjectionOrder() {
        // Deliberately constructed out of alphabetical order, mirroring how
        // Spring's bean injection order is not guaranteed to match declaration order.
        RecordingRule ruleC = recordingRule("RULE_C");
        RecordingRule ruleA = recordingRule("RULE_A");
        RecordingRule ruleB = recordingRule("RULE_B");
        VerdictEngine engine = new VerdictEngine(List.of(ruleC, ruleA, ruleB));

        engine.evaluate(context());

        assertThat(List.of(ruleA.invokedAt, ruleB.invokedAt, ruleC.invokedAt))
                .isSortedAccordingTo(Integer::compareTo);
    }

    @Test
    void evaluate_isolatesAThrowingRuleWithoutAffectingOtherRules() {
        VerdictRule throwing = new VerdictRule() {
            public String id() {
                return "BROKEN_RULE";
            }

            public String description() {
                return "always throws";
            }

            public List<VerdictReason> evaluate(VerdictContext context) {
                throw new IllegalStateException("simulated rule bug");
            }
        };
        VerdictEngine engine = new VerdictEngine(
                List.of(throwing, fixedReasonRule("HEALTHY_RULE", reason("HEALTHY_RULE", true))));

        VerdictResult result = engine.evaluate(context());

        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0).ruleId()).isEqualTo("HEALTHY_RULE");
        // The healthy rule's blocking reason still determines the outcome -
        // one broken rule must not suppress a real block.
        assertThat(result.outcome()).isEqualTo(VerdictOutcome.BLOCKED);
    }

    @Test
    void evaluate_treatsANullReturnFromARuleAsNoReasonsRatherThanThrowing() {
        VerdictRule returnsNull = new VerdictRule() {
            public String id() {
                return "NULL_RETURNING_RULE";
            }

            public String description() {
                return "misbehaves";
            }

            public List<VerdictReason> evaluate(VerdictContext context) {
                return null;
            }
        };
        VerdictEngine engine = new VerdictEngine(List.of(returnsNull));

        VerdictResult result = engine.evaluate(context());

        assertThat(result.reasons()).isEmpty();
        assertThat(result.outcome()).isEqualTo(VerdictOutcome.APPROVED);
    }

    @Test
    void evaluate_isSafeToCallConcurrentlyFromMultipleThreadsWithDistinctContexts() throws InterruptedException {
        VerdictEngine engine = new VerdictEngine(List.of(fixedReasonRule("RULE_A", reason("RULE_A", true))));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<VerdictResult>> futures = IntStream.range(0, 50)
                    .mapToObj(i -> CompletableFuture.supplyAsync(
                            () -> engine.evaluate(new VerdictContext((long) i, "org/repo", List.of(), List.of())),
                            executor))
                    .toList();

            List<VerdictResult> results = futures.stream().map(CompletableFuture::join).toList();

            // Every concurrent call must see its own analysisRunId reflected back correctly -
            // any cross-thread state leakage in VerdictEngine would show up as a mismatch here.
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).analysisRunId()).isEqualTo((long) i);
                assertThat(results.get(i).outcome()).isEqualTo(VerdictOutcome.BLOCKED);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private VerdictContext context() {
        return new VerdictContext(1L, "org/repo", List.of(), List.of());
    }

    private VerdictReason reason(String ruleId, boolean blocking) {
        return new VerdictReason(ruleId, "message for " + ruleId, blocking);
    }

    private VerdictRule fixedReasonRule(String id, VerdictReason reason) {
        return new VerdictRule() {
            public String id() {
                return id;
            }

            public String description() {
                return "test rule " + id;
            }

            public List<VerdictReason> evaluate(VerdictContext context) {
                return List.of(reason);
            }
        };
    }

    private RecordingRule recordingRule(String id) {
        return new RecordingRule(id);
    }

    /** Records the global invocation sequence number it was called at, to assert ordering. */
    private static final class RecordingRule implements VerdictRule {
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

        public List<VerdictReason> evaluate(VerdictContext context) {
            invokedAt = SEQUENCE.getAndIncrement();
            return List.of();
        }
    }
}
