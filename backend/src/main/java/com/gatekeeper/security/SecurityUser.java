package com.gatekeeper.security;

import com.gatekeeper.security.authorization.RolePermissions;
import com.gatekeeper.user.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class SecurityUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String roleName;
    private final Long organizationId;
    private final boolean enabled;

    public SecurityUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.roleName = user.getRole().getName();
        this.organizationId = user.getOrganization().getId();
        this.enabled = user.isEnabled();
    }

    /**
     * ROLE_x is kept for any code that still checks it, but every controller
     * in this codebase authorizes on {@link com.gatekeeper.security.authorization.Permission}
     * names instead (Milestone 5: RBAC Enforcement) - see
     * docs/Authorization-Model.md. RolePermissions.forRole is the single
     * place that translates this user's role into what they may actually do;
     * an unrecognized role name (see RolePermissions' own Javadoc) simply
     * contributes no permission authorities, not an error.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
        RolePermissions.forRole(roleName).forEach(
                permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
