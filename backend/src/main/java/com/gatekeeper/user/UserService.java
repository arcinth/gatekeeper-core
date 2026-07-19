package com.gatekeeper.user;

import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.auditlog.AuditTargetType;
import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.role.Role;
import com.gatekeeper.role.RoleRepository;
import com.gatekeeper.user.dto.CreateUserRequest;
import com.gatekeeper.user.dto.UpdateUserRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User create(CreateUserRequest request, Long actorId) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with email '" + request.email() + "' already exists.");
        }
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.roleId()));

        User user = User.builder()
                .organization(organizationService.getDefaultOrganization())
                .role(role)
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .enabled(true)
                .build();
        User saved = userRepository.save(user);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.USER_CREATED)
                .organizationId(saved.getOrganization().getId())
                .actorId(actorId)
                .targetType(AuditTargetType.USER)
                .targetId(String.valueOf(saved.getId()))
                .newValue(Map.of("email", saved.getEmail(), "fullName", saved.getFullName(), "role", role.getName()))
                .summary("User '" + saved.getEmail() + "' created.")
                .build());

        return saved;
    }

    @Transactional
    public User update(Long id, UpdateUserRequest request, Long actorId) {
        User user = findById(id);
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.roleId()));

        Map<String, Object> oldValue = Map.of(
                "fullName", user.getFullName(), "role", user.getRole().getName(), "enabled", user.isEnabled());

        user.setFullName(request.fullName());
        user.setRole(role);
        user.setEnabled(request.enabled());
        User saved = userRepository.save(user);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.USER_UPDATED)
                .organizationId(saved.getOrganization().getId())
                .actorId(actorId)
                .targetType(AuditTargetType.USER)
                .targetId(String.valueOf(saved.getId()))
                .oldValue(oldValue)
                .newValue(Map.of(
                        "fullName", saved.getFullName(), "role", role.getName(), "enabled", saved.isEnabled()))
                .summary("User '" + saved.getEmail() + "' updated.")
                .build());

        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        User user = findById(id);
        Long organizationId = user.getOrganization().getId();
        String email = user.getEmail();
        userRepository.delete(user);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.USER_REMOVED)
                .organizationId(organizationId)
                .actorId(actorId)
                .targetType(AuditTargetType.USER)
                .targetId(String.valueOf(id))
                .oldValue(Map.of("email", email))
                .summary("User '" + email + "' removed.")
                .build());
    }
}
