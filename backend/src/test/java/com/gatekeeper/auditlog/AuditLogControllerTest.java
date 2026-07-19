package com.gatekeeper.auditlog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.auditlog.dto.AuditLogSummaryResponse;
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
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves AUDIT_LOG_READ gates every method on this controller (Milestone 7:
 * Enterprise Audit Logging) - business logic itself belongs to
 * AuditLogService; this class exists to prove the authorization layer, the
 * same shape as PolicyConfigurationControllerTest.
 */
@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void search_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void search_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void search_returns200WhenTheCallerHasAuditLogRead() throws Exception {
        authenticateAs("admin@example.com", 3L, "ADMINISTRATOR");
        AuditLogSummaryResponse entry = new AuditLogSummaryResponse(
                1L, AuditEventType.USER_CREATED, "User created.", 3L,
                null, null, null, null, null, 9L, "Ada",
                null, null, null, null, "corr-1", Instant.now());
        when(auditLogService.search(eq(3L), any(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(entry), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].summary").value("User created."));
    }

    @Test
    void findById_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");

        mockMvc.perform(get("/api/v1/audit-logs/1").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_returns200WithCsvContentTypeWhenTheCallerHasAuditLogRead() throws Exception {
        authenticateAs("devsecops@example.com", 3L, "DEVSECOPS_ENGINEER");
        when(auditLogService.exportCsv(eq(3L), any())).thenReturn("id,eventType\n".getBytes());

        mockMvc.perform(get("/api/v1/audit-logs/export").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void export_returns403WhenTheCallerIsADeveloper() throws Exception {
        authenticateAs("developer@example.com", 3L, "DEVELOPER");

        mockMvc.perform(get("/api/v1/audit-logs/export").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
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
