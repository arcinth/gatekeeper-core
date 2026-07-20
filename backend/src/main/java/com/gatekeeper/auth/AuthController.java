package com.gatekeeper.auth;

import com.gatekeeper.auth.dto.CurrentUserResponse;
import com.gatekeeper.auth.dto.LoginRequest;
import com.gatekeeper.auth.dto.LogoutRequest;
import com.gatekeeper.auth.dto.RefreshRequest;
import com.gatekeeper.auth.dto.TokenResponse;
import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.security.ratelimit.RateLimitService;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RateLimitService rateLimitService;

    /**
     * IP + account rate limits (Milestone 10: Security Hardening) run before
     * {@code authService.login} even attempts authentication - both checks
     * happen in {@link RateLimitService#checkLogin}, independently keyed, so
     * exceeding either one rejects the request.
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkLogin(httpRequest.getRemoteAddr(), request.email());
        return ApiResponse.ok("Login successful.", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkRefresh(httpRequest.getRemoteAddr());
        return ApiResponse.ok("Token refreshed successfully.", authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Logout successful.", null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<CurrentUserResponse> currentUser(@AuthenticationPrincipal SecurityUser principal) {
        User user = userService.findById(principal.getId());
        return ApiResponse.ok(CurrentUserResponse.from(user));
    }
}
