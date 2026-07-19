package com.gatekeeper.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ErrorCode.GK_422.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_422.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
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
        return ResponseEntity.status(ErrorCode.GK_409.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_409.getCode(),
                        "This operation could not be completed because the resource is referenced by other records."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(ErrorCode.GK_401.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_401.getCode(), "Invalid email or password."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.GK_401.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_401.getCode(), "Authentication is required."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.GK_403.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.GK_403.getCode(), "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(ErrorCode.GK_500.getCode(), "An unexpected error occurred."));
    }
}
