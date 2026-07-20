package com.gatekeeper.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.role.Role;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.user.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves REPOSITORY_MANAGE gates repository writes (Milestone 5: RBAC
 * Enforcement) - business logic itself is covered by RepositoryServiceTest;
 * this class exists to prove the authorization layer, not to re-test
 * update(). No POST tests: manual repository creation was removed in
 * Milestone 8 (Repository Onboarding) - GitHub App installation is the only
 * supported way a repository enters GateKeeper.
 * <p>
 * Authenticates as a real {@link SecurityUser} (not a plain Spring
 * {@code User}) - RepositoryController's write methods read
 * {@code principal.getId()} to attribute the audit trail (Milestone 7), and
 * a mismatched principal type resolves to null there, NPEing instead of
 * exercising the authorization behavior this class actually tests.
 */
@WebMvcTest(RepositoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryService repositoryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void update_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(put("/api/v1/repositories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"active\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", "DEVELOPER");

        mockMvc.perform(put("/api/v1/repositories/1")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_returns200WhenTheCallerHasRepositoryManage() throws Exception {
        authenticateAs("platform@example.com", "PLATFORM_ENGINEER");
        Repository repository = Repository.builder()
                .name("core").fullName("acme/core").active(true).build();
        when(repositoryService.update(eq(1L), any(), any())).thenReturn(repository);

        mockMvc.perform(put("/api/v1/repositories/1")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("core"));
    }

    private void authenticateAs(String email, String roleName) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);

        Organization organization = Organization.builder().name("Acme").build();
        ReflectionTestUtils.setField(organization, "id", 1L);
        User user = User.builder()
                .email(email)
                .passwordHash("x")
                .fullName("Test User")
                .organization(organization)
                .role(Role.builder().name(roleName).build())
                .enabled(true)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        SecurityUser securityUser = new SecurityUser(user);
        when(customUserDetailsService.loadUserByUsername(eq(email))).thenReturn(securityUser);
    }
}
