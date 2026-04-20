package org.ryudev.com.flowforge.config;

import org.ryudev.com.flowforge.domain.Role;
import org.ryudev.com.flowforge.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * JWT principal: {@link #getUsername()} returns {@link User#getId()} as string so the subject is
 * globally unique across tenants (email alone is not).
 */
public record FlowForgePrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        Role role,
        List<GrantedAuthority> authorities
) implements UserDetails {

    public static FlowForgePrincipal from(User user) {
        return new FlowForgePrincipal(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getRole(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    /** Subject claim for JWT — stable, unique per user. */
    @Override
    public String getUsername() {
        return userId.toString();
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
        return true;
    }
}
