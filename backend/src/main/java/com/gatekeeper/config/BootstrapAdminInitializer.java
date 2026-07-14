package com.gatekeeper.config;

import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.role.Role;
import com.gatekeeper.role.RoleName;
import com.gatekeeper.role.RoleRepository;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds exactly one Administrator account on first startup so the platform is
 * reachable at all (docs/API-Design.md's User API is admin-only, so without this
 * bootstrap account no one could ever create the first user). This is bootstrap
 * credential material, not sample business data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    @Value("${gatekeeper.bootstrap.admin.password}")
    private String bootstrapAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(bootstrapAdminEmail)) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + RoleName.ADMINISTRATOR + "' was not found. Was the Flyway migration applied?"));

        User admin = User.builder()
                .organization(organizationService.getDefaultOrganization())
                .role(adminRole)
                .email(bootstrapAdminEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode(bootstrapAdminPassword))
                .fullName("GateKeeper Administrator")
                .enabled(true)
                .build();
        userRepository.save(admin);

        log.info("Bootstrap Administrator account created for {}. Change this password immediately outside local development.", bootstrapAdminEmail);
    }
}
