package com.gatekeeper.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ORGANIZATION_ID = "orgId";
    public static final String CLAIM_EMAIL = "email";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final Key signingKey;
    private final String issuer;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public JwtService(
            @Value("${gatekeeper.security.jwt.secret}") String secret,
            @Value("${gatekeeper.security.jwt.issuer}") String issuer,
            @Value("${gatekeeper.security.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            @Value("${gatekeeper.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.issuer = issuer;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(Long userId, String email, String role, Long organizationId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_ORGANIZATION_ID, organizationId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public GeneratedRefreshToken generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtlDays, ChronoUnit.DAYS);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .setIssuer(issuer)
                .setSubject(String.valueOf(userId))
                .setId(jti)
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new GeneratedRefreshToken(token, jti, expiresAt);
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Token is invalid or expired.");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }

    public record GeneratedRefreshToken(String token, String jti, Instant expiresAt) {
    }
}
