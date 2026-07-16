package com.gatekeeper.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.role.Role;
import com.gatekeeper.role.RoleRepository;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the fix for the known release-stabilization defect: GET
 * /api/v1/users/{id} and GET /api/v1/auth/me both threw
 * LazyInitializationException because UserRepository's inherited
 * findById(Long) never eagerly fetched role/organization, unlike its
 * explicitly-overridden findAll() - see UserRepository's own Javadoc for the
 * full root cause. A plain Mockito unit test structurally cannot exercise
 * this: the bug is about a real Hibernate session closing between the
 * transactional repository call and the later, non-transactional DTO
 * mapping in the controller, which only exists with a real
 * EntityManager/session - mirrors why AnthropicAIReviewProviderRetryTest and
 * GitHubApiClientRetryTest exist as their own Spring-context tests rather
 * than relying on the plain unit tests of the same classes.
 * <p>
 * No WireMock/GitHub App harness is needed here (unlike
 * SecurityFindingQueryIntegrationTest and its siblings) - this defect and
 * its fix live entirely in the auth/user layer, with no pipeline involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UserAndAuthLazyLoadingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    private User admin;
    private String bearerToken;

    @BeforeEach
    void loadBootstrapAdminAndAuthenticate() {
        admin = userRepository.findByEmailIgnoreCase(bootstrapAdminEmail).orElseThrow();
        bearerToken = "Bearer " + jwtService.generateAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().getName(), admin.getOrganization().getId());
    }

    @Test
    void getCurrentUser_returnsTheAuthenticatedUsersProfileInsteadOfThrowing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.data.roleName").value(admin.getRole().getName()))
                .andExpect(jsonPath("$.data.organizationName").value(admin.getOrganization().getName()));
    }

    @Test
    void findUserById_returnsTheUsersProfileInsteadOfThrowing() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + admin.getId()).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(admin.getId()))
                .andExpect(jsonPath("$.data.roleName").value(admin.getRole().getName()));
    }

    @Test
    void findUserById_worksForAFreshlyCreatedNonBootstrapUserToo() throws Exception {
        Role developerRole = roleRepository.findByName("DEVELOPER").orElseThrow();
        Organization organization = organizationService.getDefaultOrganization();
        User created = userRepository.save(User.builder()
                .organization(organization)
                .role(developerRole)
                .email("lazy-loading-fix-verify@gatekeeper.local")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Lazy Loading Fix Verify")
                .enabled(true)
                .build());

        mockMvc.perform(get("/api/v1/users/" + created.getId()).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("lazy-loading-fix-verify@gatekeeper.local"))
                .andExpect(jsonPath("$.data.roleName").value("DEVELOPER"));
    }

    @Test
    void findUserById_returns404ForAnUnknownIdRatherThanA500() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999999").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }
}
