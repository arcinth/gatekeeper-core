package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-256-bits-for-hs256-signing";
    private static final String ISSUER = "gatekeeper-core";
    private static final long CLOCK_SKEW_SECONDS = 30;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ISSUER, 15, 7, CLOCK_SKEW_SECONDS);
    }

    @Test
    void generateAccessToken_producesATokenThatParseClaimsAccepts() {
        String token = jwtService.generateAccessToken(1L, "user@example.com", "DEVELOPER", 2L);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("user@example.com");
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("DEVELOPER");
        assertThat(claims.get(JwtService.CLAIM_TYPE, String.class)).isEqualTo(JwtService.TOKEN_TYPE_ACCESS);
    }

    @Test
    void parseClaims_rejectsATokenSignedWithTheSameKeyButADifferentIssuer() {
        String foreignToken = tokenWithIssuerAndExpiry("someone-else", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> jwtService.parseClaims(foreignToken)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseClaims_acceptsATokenExpiredWithinTheClockSkewTolerance() {
        // Expired 10s ago - inside the 30s tolerance configured in setUp().
        String token = tokenWithIssuerAndExpiry(ISSUER, Instant.now().minusSeconds(10));

        assertThatCode(() -> jwtService.parseClaims(token)).doesNotThrowAnyException();
    }

    @Test
    void parseClaims_rejectsATokenExpiredBeyondTheClockSkewTolerance() {
        // Expired 60s ago - outside the 30s tolerance configured in setUp().
        String token = tokenWithIssuerAndExpiry(ISSUER, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(InvalidTokenException.class);
    }

    private String tokenWithIssuerAndExpiry(String issuer, Instant expiresAt) {
        Key signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject("1")
                .setIssuedAt(Date.from(Instant.now().minusSeconds(120)))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }
}
