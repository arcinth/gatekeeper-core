package com.gatekeeper.security;

import com.gatekeeper.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Without this, findByEmailIgnoreCase's own transaction (and Hibernate
     * session) closes the instant it returns - open-in-view is disabled
     * project-wide - so SecurityUser's constructor, which reads the lazy
     * role/organization associations, throws LazyInitializationException on
     * every call. JwtAuthenticationFilter's broad catch swallows that silently
     * and clears the security context, so every JWT-authenticated request
     * failed with 401 (or, for permitAll endpoints reading @AuthenticationPrincipal,
     * a NullPointerException) with no error surfaced anywhere - found while
     * manually verifying Milestone 5's endpoints, the first time this project's
     * real login -> Bearer token -> authenticated request flow was exercised
     * end-to-end against a real database rather than mocked Spring Security test infra.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
    }
}
