package com.gatekeeper.policyconfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyconfiguration.dto.PolicyConfigurationResponse;
import com.gatekeeper.role.Role;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.user.User;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Proves POLICY_MANAGE gates writes while WORKSPACE_READ (every role) covers reads (Milestone 6: Policy Management). */
@WebMvcTest(PolicyConfigurationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PolicyConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyConfigurationService policyConfigurationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/policies")).andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returns200ForADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");
        when(policyConfigurationService.findCatalogForOrganization(3L)).thenReturn(List.of(
                new PolicyConfigurationResponse(
                        "TODO_COMMENT", "desc", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW, true, PolicySeverity.LOW, false)));

        mockMvc.perform(get("/api/v1/policies").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleId").value("TODO_COMMENT"));
    }

    @Test
    void update_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");

        mockMvc.perform(put("/api/v1/policies/TODO_COMMENT")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_returns200WhenTheCallerHasPolicyManage() throws Exception {
        authenticateAs("admin@example.com", 3L, "ADMINISTRATOR");
        when(policyConfigurationService.upsert(eq(3L), eq("TODO_COMMENT"), any(), any())).thenReturn(
                new PolicyConfigurationResponse(
                        "TODO_COMMENT", "desc", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                        false, PolicySeverity.LOW, true));

        mockMvc.perform(put("/api/v1/policies/TODO_COMMENT")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void update_returns404WhenTheRuleIdIsUnrecognized() throws Exception {
        authenticateAs("admin@example.com", 3L, "ADMINISTRATOR");
        when(policyConfigurationService.upsert(eq(3L), eq("NOT_A_REAL_RULE"), any(), any()))
                .thenThrow(new ResourceNotFoundException("No PolicyRule found with id: NOT_A_REAL_RULE"));

        mockMvc.perform(put("/api/v1/policies/NOT_A_REAL_RULE")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void update_returns422WhenEnabledIsMissing() throws Exception {
        authenticateAs("admin@example.com", 3L, "ADMINISTRATOR");

        mockMvc.perform(put("/api/v1/policies/TODO_COMMENT")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resetToDefault_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");

        mockMvc.perform(delete("/api/v1/policies/TODO_COMMENT").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetToDefault_returns200WhenTheCallerHasPolicyManage() throws Exception {
        authenticateAs("platform@example.com", 3L, "PLATFORM_ENGINEER");
        when(policyConfigurationService.resetToDefault(eq(3L), eq("TODO_COMMENT"), any())).thenReturn(
                new PolicyConfigurationResponse(
                        "TODO_COMMENT", "desc", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                        true, PolicySeverity.LOW, false));

        mockMvc.perform(delete("/api/v1/policies/TODO_COMMENT").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overridden").value(false));
    }

    private void authenticateAs(String email, Long organizationId, String roleName) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);

        Organization organization = Organization.builder().name("Acme").build();
        ReflectionTestUtils.setField(organization, "id", organizationId);
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
