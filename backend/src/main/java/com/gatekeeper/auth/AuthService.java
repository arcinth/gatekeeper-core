package com.gatekeeper.auth;

import com.gatekeeper.auth.dto.LoginRequest;
import com.gatekeeper.auth.dto.TokenResponse;
import com.gatekeeper.security.InvalidTokenException;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new InvalidTokenException("Invalid email or password."));

        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshTokenValue) {
        Claims claims = jwtService.parseClaims(refreshTokenValue);
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw new InvalidTokenException("Token is not a refresh token.");
        }

        String jti = claims.getId();
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(jti))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is unknown or has already been used."));

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired or was revoked.");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        return issueTokens(user);
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
