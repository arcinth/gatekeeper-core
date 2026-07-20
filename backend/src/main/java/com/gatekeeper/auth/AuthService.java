package com.gatekeeper.auth;

import com.gatekeeper.auth.dto.LoginRequest;
import com.gatekeeper.auth.dto.TokenResponse;
import com.gatekeeper.security.InvalidTokenException;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final MeterRegistry meterRegistry;

    /**
     * outcome=success/failure tags on gatekeeper.auth.login.attempts (Milestone
     * 9: Observability) - never the attempted email, which would be both a
     * cardinality and a PII problem; a login-failure spike is visible from the
     * counter alone; a specific account's failures are a job for the audit
     * trail (Milestone 7) or log correlation, not a metric tag.
     */
    public TokenResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            User user = userRepository.findByEmailIgnoreCase(request.email())
                    .orElseThrow(() -> new InvalidTokenException("Invalid email or password."));

            TokenResponse response = issueTokens(user);
            meterRegistry.counter("gatekeeper.auth.login.attempts", "outcome", "success").increment();
            return response;
        } catch (RuntimeException ex) {
            meterRegistry.counter("gatekeeper.auth.login.attempts", "outcome", "failure").increment();
            throw ex;
        }
    }

    public TokenResponse refresh(String refreshTokenValue) {
        try {
            Claims claims = jwtService.parseClaims(refreshTokenValue);
            if (!JwtService.TOKEN_TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
                throw new InvalidTokenException("Token is not a refresh token.");
            }

            String jti = claims.getId();
            RefreshToken storedToken = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(jti))
                    .orElseThrow(() -> new InvalidTokenException("Refresh token is unknown or has already been used."));

            /*
             * Reuse detection (Milestone 10: Security Hardening): a refresh token
             * that is already revoked was legitimately consumed once already (by
             * this same rotation flow, a few lines below) - presenting it again is
             * the textbook signal of a stolen-and-replayed token, distinct from a
             * token that simply expired naturally. Logged/counted separately so an
             * operator can alert on it; the caller still just sees "invalid or
             * expired" either way, the same response as any other rejected token,
             * so this doesn't tell an attacker which case they hit.
             */
            if (storedToken.isRevoked()) {
                log.warn("Refresh token reuse detected for user {} - a previously-revoked refresh token was presented again.",
                        storedToken.getUser().getId());
                meterRegistry.counter("gatekeeper.security.refresh_token_reuse").increment();
                throw new InvalidTokenException("Refresh token has expired or was revoked.");
            }
            if (storedToken.getExpiresAt().isBefore(Instant.now())) {
                throw new InvalidTokenException("Refresh token has expired or was revoked.");
            }

            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);

            User user = storedToken.getUser();
            TokenResponse response = issueTokens(user);
            meterRegistry.counter("gatekeeper.auth.token.refresh", "outcome", "success").increment();
            return response;
        } catch (RuntimeException ex) {
            meterRegistry.counter("gatekeeper.auth.token.refresh", "outcome", "failure").increment();
            throw ex;
        }
    }

    public void logout(String refreshTokenValue) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(refreshTokenValue);
        } catch (InvalidTokenException ex) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(claims.getId()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().getName(), user.getOrganization().getId());

        JwtService.GeneratedRefreshToken generatedRefreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(generatedRefreshToken.jti()))
                .expiresAt(generatedRefreshToken.expiresAt())
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(
                accessToken,
                generatedRefreshToken.token(),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds());
    }
}
