package com.gatekeeper.user;

import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.role.Role;
import com.gatekeeper.role.RoleRepository;
import com.gatekeeper.user.dto.CreateUserRequest;
import com.gatekeeper.user.dto.UpdateUserRequest;
import java.util.List;
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
    public User create(CreateUserRequest request) {
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
        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, UpdateUserRequest request) {
        User user = findById(id);
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.roleId()));

        user.setFullName(request.fullName());
        user.setRole(role);
        user.setEnabled(request.enabled());
        return userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }
}
