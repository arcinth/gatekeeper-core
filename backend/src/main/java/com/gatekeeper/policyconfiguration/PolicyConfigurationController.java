package com.gatekeeper.policyconfiguration;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.policyconfiguration.dto.PolicyConfigurationResponse;
import com.gatekeeper.policyconfiguration.dto.UpdatePolicyConfigurationRequest;
import com.gatekeeper.security.SecurityUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization-scoped policy configuration (Milestone 6: Policy Management).
 * No POST: the rule catalog is entirely code-defined (PolicyRule beans
 * discovered by Spring, see docs/Policy-Development.md) - organizations
 * configure an existing rule, they never create one. {@code ruleId} in every
 * path is one of those beans' {@code id()}; an unrecognized id 404s.
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PolicyConfigurationController {

    private final PolicyConfigurationService policyConfigurationService;

    @GetMapping
    @PreAuthorize("hasAuthority('WORKSPACE_READ')")
    public ApiResponse<List<PolicyConfigurationResponse>> findAll(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(policyConfigurationService.findCatalogForOrganization(principal.getOrganizationId()));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('POLICY_MANAGE')")
    public ApiResponse<PolicyConfigurationResponse> update(
            @PathVariable String ruleId,
            @Valid @RequestBody UpdatePolicyConfigurationRequest request,
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(
                "Policy configuration updated successfully.",
                policyConfigurationService.upsert(principal.getOrganizationId(), ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('POLICY_MANAGE')")
    public ApiResponse<PolicyConfigurationResponse> resetToDefault(
            @PathVariable String ruleId, @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(
                "Policy configuration reset to default.",
                policyConfigurationService.resetToDefault(principal.getOrganizationId(), ruleId));
    }
}
