package com.gatekeeper.role;

import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.role.dto.CreateRoleRequest;
import com.gatekeeper.role.dto.UpdateRoleRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    @Transactional
    public Role create(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(Long id, UpdateRoleRequest request) {
        Role role = findById(id);
        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        role.setName(request.name());
        role.setDescription(request.description());
        return roleRepository.save(role);
    }

    /**
     * Relies on the users.role_id foreign key (NO ACTION on delete) rather than a
     * cross-module existsByRoleId() pre-check, so this package no longer depends on
     * the user package. The FK violation is caught here - not in a global handler -
     * so the 409 message can still name the role, using data already in scope.
     */
    @Transactional
    public void delete(Long id) {
        Role role = findById(id);
        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Role '" + role.getName() + "' is assigned to one or more users and cannot be deleted.");
        }
    }
}
