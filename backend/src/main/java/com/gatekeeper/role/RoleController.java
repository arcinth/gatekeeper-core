package com.gatekeeper.role;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.role.dto.CreateRoleRequest;
import com.gatekeeper.role.dto.RoleResponse;
import com.gatekeeper.role.dto.UpdateRoleRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<List<RoleResponse>> findAll() {
        List<RoleResponse> roles = roleService.findAll().stream().map(RoleResponse::from).toList();
        return ApiResponse.ok(roles);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(RoleResponse.from(roleService.findById(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok("Role created successfully.", RoleResponse.from(roleService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated successfully.", RoleResponse.from(roleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }
}
