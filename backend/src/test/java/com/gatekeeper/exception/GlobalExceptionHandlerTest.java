package com.gatekeeper.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Verifies each handler both preserves its pre-existing response shape/status
 * (Milestone 9 mandates zero response-contract changes) and increments
 * {@code gatekeeper.errors.total} with the expected category/error_code tags.
 */
class GlobalExceptionHandlerTest {

    private SimpleMeterRegistry meterRegistry;
    private GlobalExceptionHandler handler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable(any(Supplier.class))).thenReturn(meterRegistry);
        handler = new GlobalExceptionHandler(meterRegistryProvider);
    }

    @Test
    void handleApiException_preservesTheErrorCodesStatusAndBody_andRecordsItsCategory() {
        ApiException ex = new ApiException(ErrorCode.GK_404, "Repository not found.");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("GK-404");
        assertThat(response.getBody().error().message()).isEqualTo("Repository not found.");
        assertCounted("business", "GK-404");
    }

    @Test
    void handleConstraintViolation_returns422_andRecordsValidationCategory() {
        ConstraintViolationException ex = new ConstraintViolationException("invalid", Set.of());

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().error().code()).isEqualTo("GK-422");
        assertCounted("validation", "GK-422");
    }

    @Test
    void handleDataIntegrityViolation_returns409_andRecordsDatabaseCategory() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("fk violation");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("GK-409");
        assertCounted("database", "GK-409");
    }

    @Test
    void handleBadCredentials_returns401_withAGenericMessage_neverTheAttemptedEmail() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials for user someone@example.com");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadCredentials(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error().message()).doesNotContain("someone@example.com");
        assertCounted("authentication", "GK-401");
    }

    @Test
    void handleAuthenticationException_returns401_andRecordsAuthenticationCategory() {
        AuthenticationException ex = new UsernameNotFoundException("no such user");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertCounted("authentication", "GK-401");
    }

    @Test
    void handleAccessDenied_returns403_andRecordsAuthorizationCategory() {
        AccessDeniedException ex = new AccessDeniedException("denied");

        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertCounted("authorization", "GK-403");
    }

    @Test
    void handleNoResourceFound_returns404AsABusinessCategory_notAnUnexpectedError() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "actuator/env");

        ResponseEntity<ApiErrorResponse> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().code()).isEqualTo("GK-404");
        assertCounted("business", "GK-404");
    }

    @Test
    void handleUnexpectedException_returns500_andRecordsUnexpectedCategory() {
        Exception ex = new RuntimeException("boom");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo("GK-500");
        assertCounted("unexpected", "GK-500");
    }

    private void assertCounted(String category, String errorCode) {
        var counter = meterRegistry.find("gatekeeper.errors.total")
                .tag("category", category)
                .tag("error_code", errorCode)
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
