package com.foodloop.security;

import com.foodloop.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated principal placed in the SecurityContext by JwtAuthFilter. */
public class AuthUser implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;

    public AuthUser(Long id, String email, String passwordHash, String role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static AuthUser from(User u) {
        boolean enabled = u.getStatus() == com.foodloop.entity.enums.UserStatus.ACTIVE
                || u.getStatus() == com.foodloop.entity.enums.UserStatus.PENDING;
        return new AuthUser(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole().getName(), enabled);
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
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
