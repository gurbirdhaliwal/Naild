package com.knappi.security.user;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstname;
    private String lastname;

    @Column(nullable = false)
    private String username;

    @Column(unique = true,nullable = false)
    private String email;

    @Column(unique = true,nullable = false)
    @JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    private LocalDateTime passwordChangedAt;

    @Column(length = 64)
    @JsonIgnore
    private String passwordResetTokenHash;

    @JsonIgnore
    private LocalDateTime passwordResetTokenExpiresAt;

    @Column(length = 100)
    @JsonIgnore
    private String loginVerificationCodeHash;

    @JsonIgnore
    private LocalDateTime loginVerificationCodeExpiresAt;

    @JsonIgnore
    private Integer loginVerificationAttempts;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (passwordChangedAt == null) {
            passwordChangedAt = createdAt;
        }
    }

    public void markPasswordChangedNow() {
        passwordChangedAt = LocalDateTime.now();
    }
}
