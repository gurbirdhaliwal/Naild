package com.naild.security.auth;

import com.naild.security.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedUserResponse {
    private User user;
    private Instant sessionExpiresAt;
    private long sessionDurationSeconds;
    private String token;
}
