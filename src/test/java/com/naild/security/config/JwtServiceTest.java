package com.naild.security.config;

import com.naild.security.user.Role;
import com.naild.security.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final JwtService jwtService = new JwtService(TEST_SECRET_KEY);

    @Test
    void tokenIsValidWhenIssuedAfterLatestPasswordChange() {
        User user = localUser();
        user.setPasswordChangedAt(LocalDateTime.now().minusMinutes(5));

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenBecomesInvalidAfterPasswordChange() {
        User user = localUser();
        String token = jwtService.generateToken(user);

        Date issuedAt = jwtService.extractClaims(token, claims -> claims.getIssuedAt());
        user.setPasswordChangedAt(LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault()).plusSeconds(1));

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    private User localUser() {
        return User.builder()
                .id(1L)
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .authProvider("LOCAL")
                .build();
    }
}
