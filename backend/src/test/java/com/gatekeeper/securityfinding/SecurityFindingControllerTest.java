package com.gatekeeper.securityfinding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.dto.SecurityFindingResponse;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecurityFindingController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SecurityFindingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecurityFindingQueryService securityFindingQueryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/security-findings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returnsThePagedResultWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        SecurityFindingResponse finding = new SecurityFindingResponse(1L, 9L, "org/core", 21, "sha", "HARDCODED_SECRET",
                SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.CRITICAL, "src/Config.java", 1,
                "Possible hardcoded secret found", "Remove the literal value.", Instant.now());
        Page<SecurityFindingResponse> page = new PageImpl<>(List.of(finding), PageRequest.of(0, 20), 1);
        when(securityFindingQueryService.findPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/security-findings?analysisRunId=9").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ruleId").value("HARDCODED_SECRET"))
                .andExpect(jsonPath("$.data.content[0].repositoryFullName").value("org/core"));
    }

    @Test
    void findById_returns404WhenTheServiceThrowsResourceNotFound() throws Exception {
        authenticateAs("dev@example.com");
        when(securityFindingQueryService.findByIdOrThrow(404L))
                .thenThrow(new ResourceNotFoundException("SecurityFinding not found with id: 404"));

        mockMvc.perform(get("/api/v1/security-findings/404").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void findAll_returns400WhenTheSeverityFilterIsAnInvalidEnumValue() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/security-findings?severity=NOT_A_SEVERITY").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    private void authenticateAs(String email) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        UserDetails userDetails = User.withUsername(email).password("x").authorities("ROLE_DEVELOPER").build();
        when(customUserDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
    }
}
