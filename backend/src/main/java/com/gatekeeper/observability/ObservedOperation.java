package com.gatekeeper.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for timing and slow-call logging (Milestone 9:
 * Observability), handled by {@link ObservedOperationAspect}. Deliberately
 * annotation-driven and applied to a small, explicit list of methods rather
 * than a broad AOP pointcut over a whole package - a package-wide pointcut
 * would instrument methods nobody asked to monitor and make it unclear, from
 * reading a class, which of its methods are actually observed. An explicit
 * annotation on each observed method answers that at a glance.
 * <p>
 * {@code value} becomes the {@code operation} tag on the
 * {@code gatekeeper.operation.duration} Timer and appears in the slow-call
 * WARN log line - keep it a short, stable, dot-separated name (e.g.
 * {@code "github.fetchPullRequestFiles"}, {@code "policy.evaluate"}), since
 * it is effectively a permanent metric/log identifier once anyone builds a
 * dashboard or alert on it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObservedOperation {

    /** Stable operation name - becomes the {@code operation} tag on the duration Timer. */
    String value();

    /** Which slow-operation threshold (see {@code gatekeeper.observability.thresholds.*}) applies to this call. */
    OperationCategory category();
}
