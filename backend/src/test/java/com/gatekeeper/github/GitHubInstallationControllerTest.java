package com.gatekeeper.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.role.Role;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.security.ratelimit.RateLimitService;
import com.gatekeeper.user.User;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the Repository Onboarding permission model (Milestone 8):
 * WORKSPACE_READ covers viewing installations, REPOSITORY_MANAGE is required
 * to fetch the install URL or trigger a resync - business logic itself is
 * covered by GitHubInstallationServiceTest/GitHubRepositorySyncServiceTest.
 */
@WebMvcTest(GitHubInstallationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "gatekeeper.github.app.id=12345",
        "gatekeeper.github.app.slug=gatekeeper-core"
})
class GitHubInstallationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubInstallationService gitHubInstallationService;

    @MockBean
    private GitHubRepositorySyncService gitHubRepositorySyncService;

    @MockBean
    private RepositoryRepository repositoryRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/github/installations")).andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returns200ForADeveloper() throws Exception {
        authenticateAs("developer@example.com", "DEVELOPER");
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(1L).githubAccountLogin("octocat").status(GitHubInstallationStatus.ACTIVE).build();
        ReflectionTestUtils.setField(installation, "id", 9L);
        when(gitHubInstallationService.findAll()).thenReturn(List.of(installation));
        when(repositoryRepository.countByGithubInstallationId(9L)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/github/installations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].githubAccountLogin").value("octocat"))
                .andExpect(jsonPath("$.data[0].repositoryCount").value(3))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void getInstallUrl_returns403ForADeveloper() throws Exception {
        authenticateAs("developer@example.com", "DEVELOPER");

        mockMvc.perform(get("/api/v1/github/install-url").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInstallUrl_returns200WithAConfiguredUrlForAPlatformEngineer() throws Exception {
        authenticateAs("platform@example.com", "PLATFORM_ENGINEER");

        mockMvc.perform(get("/api/v1/github/install-url").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appConfigured").value(true))
                .andExpect(jsonPath("$.data.url").value("https://github.com/apps/gatekeeper-core/installations/new"));
    }

    @Test
    void sync_returns403ForADeveloper() throws Exception {
        authenticateAs("developer@example.com", "DEVELOPER");

        mockMvc.perform(post("/api/v1/github/installations/9/sync").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sync_triggersSynchronizationAndReturnsTheRefreshedInstallationForAPlatformEngineer() throws Exception {
        authenticateAs("platform@example.com", "PLATFORM_ENGINEER");
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(147259549L).githubAccountLogin("arcinth").status(GitHubInstallationStatus.ACTIVE).build();
        ReflectionTestUtils.setField(installation, "id", 9L);
        when(gitHubInstallationService.findByIdOrThrow(9L)).thenReturn(installation);
        when(repositoryRepository.countByGithubInstallationId(9L)).thenReturn(5L);

        mockMvc.perform(post("/api/v1/github/installations/9/sync").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositoryCount").value(5));

        verify(gitHubRepositorySyncService).synchronize(147259549L);
    }

    @Test
    void reconcile_returns403ForADeveloper() throws Exception {
        authenticateAs("developer@example.com", "DEVELOPER");

        mockMvc.perform(post("/api/v1/github/installations/reconcile?installationId=147338541")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconcile_fetchesFromGitHubAndReturnsTheUpsertedInstallationForAPlatformEngineer() throws Exception {
        authenticateAs("platform@example.com", "PLATFORM_ENGINEER");
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(147338541L).githubAccountLogin("arcinth").status(GitHubInstallationStatus.CONNECTING).build();
        ReflectionTestUtils.setField(installation, "id", 9L);
        when(gitHubInstallationService.reconcileInstallation(147338541L)).thenReturn(installation);
        when(repositoryRepository.countByGithubInstallationId(9L)).thenReturn(0L);

        mockMvc.perform(post("/api/v1/github/installations/reconcile?installationId=147338541")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAccountLogin").value("arcinth"));

        verify(gitHubInstallationService).reconcileInstallation(147338541L);
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
