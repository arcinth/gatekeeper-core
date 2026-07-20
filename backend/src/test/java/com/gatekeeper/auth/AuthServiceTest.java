package com.gatekeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.auth.dto.LoginRequest;
import com.gatekeeper.auth.dto.TokenResponse;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.role.Role;
import com.gatekeeper.security.InvalidTokenException;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Uses a real {@link JwtService} rather than a mock - refresh-token rotation
 * and reuse detection (Milestone 10: Security Hardening) hinge on the real
 * jti round trip between mint and parse, which a mocked JwtService would
 * have to reimplement anyway.
 */
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtService jwtService =
            new JwtService("test-secret-key-with-at-least-256-bits-for-hs256-signing", "gatekeeper-core", 15, 7, 30);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AuthService authService =
            new AuthService(authenticationManager, userRepository, refreshTokenRepository, jwtService, meterRegistry);

    @Test
    void login_incrementsTheSuccessCounterOnSuccessfulAuthentication() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user(1L)));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.login(new LoginRequest(EMAIL, "password"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(meterRegistry.counter("gatekeeper.auth.login.attempts", "outcome", "success").count()).isEqualTo(1.0);
    }

    @Test
    void login_incrementsTheFailureCounterWhenAuthenticationManagerRejectsCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(meterRegistry.counter("gatekeeper.auth.login.attempts", "outcome", "failure").count()).isEqualTo(1.0);
    }

    @Test
    void refresh_rotatesTheTokenAndRevokesThePrevious() {
        JwtService.GeneratedRefreshToken generated = jwtService.generateRefreshToken(1L);
        RefreshToken stored = storedToken(generated, false, generated.expiresAt());
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(generated.jti()))).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.refresh(generated.token());

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(meterRegistry.counter("gatekeeper.auth.token.refresh", "outcome", "success").count()).isEqualTo(1.0);
    }

    /** Milestone 10: Security Hardening - the reuse-detection path added to AuthService.refresh. */
    @Test
    void refresh_detectsReuseOfAnAlreadyRevokedToken_andIncrementsTheDedicatedReuseMetric() {
        JwtService.GeneratedRefreshToken generated = jwtService.generateRefreshToken(1L);
        RefreshToken stored = storedToken(generated, true, generated.expiresAt());
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(generated.jti()))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(generated.token())).isInstanceOf(InvalidTokenException.class);

        assertThat(meterRegistry.counter("gatekeeper.security.refresh_token_reuse").count()).isEqualTo(1.0);
    }

    @Test
    void refresh_rejectsAnExpiredButNeverRevokedToken_withoutIncrementingTheReuseMetric() {
        JwtService.GeneratedRefreshToken generated = jwtService.generateRefreshToken(1L);
        // Only the persisted row's expiry is in the past - the JWT's own exp claim is
        // still valid (7 days out), so this exercises AuthService's own expiry check,
        // not JwtService.parseClaims rejecting the token first.
        RefreshToken stored = storedToken(generated, false, Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(generated.jti()))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(generated.token())).isInstanceOf(InvalidTokenException.class);

        assertThat(meterRegistry.find("gatekeeper.security.refresh_token_reuse").counter()).isNull();
    }

    private RefreshToken storedToken(JwtService.GeneratedRefreshToken generated, boolean revoked, Instant expiresAt) {
        return RefreshToken.builder()
                .user(user(1L))
                .tokenHash(TokenHasher.sha256(generated.jti()))
                .expiresAt(expiresAt)
                .revoked(revoked)
                .build();
    }

    private User user(Long id) {
        Organization organization = Organization.builder().name("Acme").build();
        ReflectionTestUtils.setField(organization, "id", 1L);
        User user = User.builder()
                .email(EMAIL)
                .passwordHash("hash")
                .fullName("Test User")
                .organization(organization)
                .role(Role.builder().name("DEVELOPER").build())
                .enabled(true)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
