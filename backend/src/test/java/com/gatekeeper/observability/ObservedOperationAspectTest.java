package com.gatekeeper.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * Weaves {@link ObservedOperationAspect} around a plain test target via
 * {@link AspectJProxyFactory} - no Spring context needed, mirroring how this
 * codebase already unit-tests other pure-logic classes without bootstrapping
 * a container.
 */
class ObservedOperationAspectTest {

    private SimpleMeterRegistry meterRegistry;
    private TestTarget proxiedTarget;
    private ListAppender<ILoggingEvent> logAppender;

    interface TestTarget {
        String fast();

        String slow();

        String failing();
    }

    static class TestTargetImpl implements TestTarget {
        @Override
        @ObservedOperation(value = "test.fast", category = OperationCategory.POLICY_ENGINE)
        public String fast() {
            return "ok";
        }

        @Override
        @ObservedOperation(value = "test.slow", category = OperationCategory.POLICY_ENGINE)
        public String slow() {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        }

        @Override
        @ObservedOperation(value = "test.failing", category = OperationCategory.GITHUB_API)
        public String failing() {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // A 0ms policy-evaluation threshold and a large github-api threshold, so "slow"
        // deterministically exceeds its threshold and "fast"/"failing" deterministically don't.
        ObservedOperationAspect aspect = new ObservedOperationAspect(meterRegistry, 60_000, 0, 60_000, 60_000, 60_000);
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestTargetImpl());
        factory.addAspect(aspect);
        proxiedTarget = factory.getProxy();

        Logger logger = (Logger) LoggerFactory.getLogger(ObservedOperationAspect.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ObservedOperationAspect.class)).detachAppender(logAppender);
    }

    @Test
    void around_recordsATimerTaggedByOperationCategoryAndOutcome() {
        proxiedTarget.fast();

        var timer = meterRegistry.find("gatekeeper.operation.duration")
                .tag("operation", "test.fast")
                .tag("category", "POLICY_ENGINE")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void around_tagsOutcomeAsErrorAndRethrowsWhenTheMethodThrows() {
        assertThatThrownBy(() -> proxiedTarget.failing()).isInstanceOf(IllegalStateException.class);

        var timer = meterRegistry.find("gatekeeper.operation.duration")
                .tag("operation", "test.failing")
                .tag("outcome", "error")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void around_logsAWarningWhenTheThresholdForItsCategoryIsExceeded() {
        proxiedTarget.slow();

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("test.slow").contains("POLICY_ENGINE");
                });
    }

    @Test
    void around_doesNotLogAWarningWhenWithinThreshold() {
        proxiedTarget.fast();

        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }
}
