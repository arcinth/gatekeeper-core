package com.gatekeeper.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves REPOSITORY_MANAGE gates repository writes (Milestone 5: RBAC
 * Enforcement) - business logic itself is covered by RepositoryServiceTest;
 * this class exists to prove the authorization layer, not to re-test create().
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
    void create_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"fullName\":\"acme/core\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", "ROLE_DEVELOPER", "WORKSPACE_READ");

        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"fullName\":\"acme/core\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201WhenTheCallerHasRepositoryManage() throws Exception {
        authenticateAs("platform@example.com", "ROLE_PLATFORM_ENGINEER", "WORKSPACE_READ", "REPOSITORY_MANAGE");
        Repository repository = Repository.builder()
                .name("core").fullName("acme/core").active(true).build();
        when(repositoryService.create(any())).thenReturn(repository);

        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"core\",\"fullName\":\"acme/core\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("core"));
    }

    private void authenticateAs(String email, String... authorities) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        UserDetails userDetails = User.withUsername(email).password("x").authorities(authorities).build();
        when(customUserDetailsService.loadUserByUsername(eq(email))).thenReturn(userDetails);
    }
}
