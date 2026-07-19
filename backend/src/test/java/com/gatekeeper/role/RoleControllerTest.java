package com.gatekeeper.role;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves ROLE_MANAGE gates every method on this controller (Milestone 5:
 * RBAC Enforcement) - business logic itself belongs to RoleService; this
 * class exists to prove the authorization layer.
 */
@WebMvcTest(RoleController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", "ROLE_DEVELOPER", "WORKSPACE_READ");

        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findAll_returns200WhenTheCallerHasRoleManage() throws Exception {
        authenticateAs("admin@example.com", "ROLE_ADMINISTRATOR", "WORKSPACE_READ", "ROLE_MANAGE");
        when(roleService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
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
