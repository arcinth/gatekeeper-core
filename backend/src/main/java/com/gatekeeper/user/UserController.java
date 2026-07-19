package com.gatekeeper.user;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.user.dto.CreateUserRequest;
import com.gatekeeper.user.dto.UpdateUserRequest;
import com.gatekeeper.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserResponse>> findAll() {
        List<UserResponse> users = userService.findAll().stream().map(UserResponse::from).toList();
        return ApiResponse.ok(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(UserResponse.from(userService.findById(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok("User created successfully.",
                UserResponse.from(userService.create(request, principal.getId())));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok("User updated successfully.",
                UserResponse.from(userService.update(id, request, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        userService.delete(id, principal.getId());
    }
}
