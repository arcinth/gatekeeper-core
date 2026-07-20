package com.gatekeeper.exception;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every handler here logs and increments {@code gatekeeper.errors.total}
 * (Milestone 9: Observability, Section 8 - Error Monitoring) in addition to
 * its pre-existing response mapping - no response body, status code, or
 * {@link ApiErrorResponse} shape changes; this milestone is purely additive.
 * <p>
 * {@code category} is derived from the fixed, small {@link ErrorCode} enum
 * (never from the exception's own message or any request data), so the tag
 * is bounded cardinality by construction. {@code GitHubApiException} and
 * {@code AIProviderException} deliberately have no handler here: neither
 * ever reaches this class in the current architecture - both are always
 * caught inside their own async orchestration layer first
 * ({@code AnalysisExecutionService}, {@code AIReviewExecutionService},
 * {@code GitHubRepositorySyncService}), which is where their failures are
 * already logged and, as of this milestone, timed with an {@code outcome=error}
 * tag by {@code ObservedOperationAspect}. Adding handlers for exception types
 * that cannot reach this class would be dead code, not additional coverage.
 * <p>
 * Takes an {@link ObjectProvider} rather than a plain {@code MeterRegistry}
 * dependency so this advice stays instantiable in a {@code @WebMvcTest} slice,
 * which - unlike the full application context - does not autoconfigure a
 * {@code MeterRegistry} bean by default; falling back to a private,
 * non-registered {@link SimpleMeterRegistry} there simply means those slice
 * tests don't assert on metrics, not that they fail to load. The real
 * application always has an actuator-backed {@code MeterRegistry} available.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String METRIC_NAME = "gatekeeper.errors.total";

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String category = categoryFor(errorCode);
        log.warn("{} [{}]: {}", category, errorCode.getCode(), ex.getMessage());
        recordError(category, errorCode.getCode());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.info("validation [{}]: {}", ErrorCode.GK_422.getCode(), message);
        recordError("validation", ErrorCode.GK_422.getCode());
        return ResponseEntity.status(ErrorCode.GK_422.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_422.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.info("validation [{}]: {}", ErrorCode.GK_422.getCode(), ex.getMessage());
        recordError("validation", ErrorCode.GK_422.getCode());
        return ResponseEntity.status(ErrorCode.GK_422.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_422.getCode(), ex.getMessage()));
    }

    /**
     * Without this, an invalid enum query param (e.g. GET /analysis-runs?status=NOT_A_STATUS)
     * fails during argument resolution - before any controller method body runs - and falls
     * through to the generic 500 handler below, logging a full stack trace for what is
     * actually a client input error (found while building Milestone 5's filterable list
     * endpoints, which are the first place this project accepts enum query parameters).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        log.info("validation [{}]: {}", ErrorCode.GK_400.getCode(), message);
        recordError("validation", ErrorCode.GK_400.getCode());
        return ResponseEntity.status(ErrorCode.GK_400.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_400.getCode(), message));
    }

    /**
     * Without this, a malformed request body - most commonly an invalid enum
     * value, e.g. POST .../review-decisions with {"decision": "MAYBE"} - fails
     * during JSON deserialization, before any @Valid constraint runs, and falls
     * through to the generic 500 handler below for what is actually a client
     * input error, the same reasoning as handleTypeMismatch above for invalid
     * enum query params.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.info("validation [{}]: Malformed or invalid request body.", ErrorCode.GK_400.getCode());
        recordError("validation", ErrorCode.GK_400.getCode());
        return ResponseEntity.status(ErrorCode.GK_400.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_400.getCode(), "Malformed or invalid request body."));
    }

    /**
     * Fallback for foreign-key/unique-constraint violations that reach the database
     * without an application-level pre-check (e.g. deletes blocked by a FK with no
     * cascade). Callers that can name the specific resource should catch this
     * closer to the source for a more specific message; this exists as a safety net.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("database [{}]: constraint violation - {}", ErrorCode.GK_409.getCode(), ex.getMostSpecificCause().getMessage());
        recordError("database", ErrorCode.GK_409.getCode());
        return ResponseEntity.status(ErrorCode.GK_409.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_409.getCode(),
                        "This operation could not be completed because the resource is referenced by other records."));
    }

    /** The attempted email is never logged here (Milestone 9: Observability - Security) - only that a login attempt failed. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("authentication [{}]: invalid credentials presented.", ErrorCode.GK_401.getCode());
        recordError("authentication", ErrorCode.GK_401.getCode());
        return ResponseEntity.status(ErrorCode.GK_401.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_401.getCode(), "Invalid email or password."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.info("authentication [{}]: {}", ErrorCode.GK_401.getCode(), ex.getMessage());
        recordError("authentication", ErrorCode.GK_401.getCode());
        return ResponseEntity.status(ErrorCode.GK_401.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_401.getCode(), "Authentication is required."));
    }

    /** INFO, not WARN: RBAC correctly denying an unauthorized caller is the system working as designed, not an anomaly. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.info("authorization [{}]: access denied.", ErrorCode.GK_403.getCode());
        recordError("authorization", ErrorCode.GK_403.getCode());
        return ResponseEntity.status(ErrorCode.GK_403.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_403.getCode(), "You do not have permission to perform this action."));
    }

    /**
     * Spring's DispatcherServlet throws this for any request path with no matching
     * handler or static resource - most commonly a client requesting a nonexistent
     * resource, or (as of Milestone 9) a request for an Actuator endpoint that
     * exists but was deliberately left off the exposure allowlist (env, beans,
     * shutdown, etc. - see docs/Observability.md). Without this handler, both cases
     * fall through to the generic 500 handler below, turning a routine "not found"
     * into an ERROR-level stack trace and a misleading gatekeeper.errors.total{
     * category="unexpected"} count - exactly the kind of noise Error Monitoring is
     * meant to prevent, not produce.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.info("business [{}]: no resource at '{}'.", ErrorCode.GK_404.getCode(), ex.getResourcePath());
        recordError("business", ErrorCode.GK_404.getCode());
        return ResponseEntity.status(ErrorCode.GK_404.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_404.getCode(), "The requested resource was not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        recordError("unexpected", ErrorCode.GK_500.getCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(ErrorCode.GK_500.getCode(), "An unexpected error occurred."));
    }

    private String categoryFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case GK_400, GK_422 -> "validation";
            case GK_401 -> "authentication";
            case GK_403 -> "authorization";
            case GK_404, GK_409 -> "business";
            case GK_500 -> "unexpected";
        };
    }

    private void recordError(String category, String errorCode) {
        meterRegistry.counter(METRIC_NAME, "category", category, "error_code", errorCode).increment();
    }
}
