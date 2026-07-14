package com.gatekeeper.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
