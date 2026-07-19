package com.gatekeeper.role;

import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.auditlog.AuditTargetType;
import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.role.dto.CreateRoleRequest;
import com.gatekeeper.role.dto.UpdateRoleRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final OrganizationService organizationService;

    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    @Transactional
    public Role create(CreateRoleRequest request, Long actorId) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .build();
        Role saved = roleRepository.save(role);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.ROLE_CREATED)
                .organizationId(organizationService.getDefaultOrganization().getId())
                .actorId(actorId)
                .targetType(AuditTargetType.ROLE)
                .targetId(String.valueOf(saved.getId()))
                .newValue(Map.of("name", saved.getName(), "description", saved.getDescription() == null ? "" : saved.getDescription()))
                .summary("Role '" + saved.getName() + "' created.")
                .build());

        return saved;
    }

    @Transactional
    public Role update(Long id, UpdateRoleRequest request, Long actorId) {
        Role role = findById(id);
        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        Map<String, Object> oldValue = Map.of(
                "name", role.getName(), "description", role.getDescription() == null ? "" : role.getDescription());

        role.setName(request.name());
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.ROLE_UPDATED)
                .organizationId(organizationService.getDefaultOrganization().getId())
                .actorId(actorId)
                .targetType(AuditTargetType.ROLE)
                .targetId(String.valueOf(saved.getId()))
                .oldValue(oldValue)
                .newValue(Map.of(
                        "name", saved.getName(), "description", saved.getDescription() == null ? "" : saved.getDescription()))
                .summary("Role '" + saved.getName() + "' updated.")
                .build());

        return saved;
    }

    /**
     * Relies on the users.role_id foreign key (NO ACTION on delete) rather than a
     * cross-module existsByRoleId() pre-check, so this package no longer depends on
     * the user package. The FK violation is caught here - not in a global handler -
     * so the 409 message can still name the role, using data already in scope.
     */
    @Transactional
    public void delete(Long id, Long actorId) {
        Role role = findById(id);
        String name = role.getName();
        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Role '" + role.getName() + "' is assigned to one or more users and cannot be deleted.");
        }

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.ROLE_REMOVED)
                .organizationId(organizationService.getDefaultOrganization().getId())
                .actorId(actorId)
                .targetType(AuditTargetType.ROLE)
                .targetId(String.valueOf(id))
                .oldValue(Map.of("name", name))
                .summary("Role '" + name + "' removed.")
                .build());
    }
}
