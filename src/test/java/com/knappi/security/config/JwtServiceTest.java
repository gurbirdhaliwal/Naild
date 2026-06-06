package com.knappi.security.config;

import com.knappi.security.user.Role;
import com.knappi.security.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    @Test
    void tokenIsValidWhenPasswordTimestampIsInSameSecondAsIssueTime() {
        User user = localUser();
        String token = jwtService.generateToken(user);

        Date issuedAt = jwtService.extractClaims(token, claims -> claims.getIssuedAt());
        user.setPasswordChangedAt(
                LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault())
                        .plus(500, ChronoUnit.MILLIS)
        );

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenContainsSessionClaimsForStatelessSessionReads() {
        User user = localUser();
        user.setAureonVoiceId("alloy");
        user.setPasswordChangedAt(LocalDateTime.now().minusMinutes(1));

        String token = jwtService.generateToken(user);
        User sessionUser = jwtService.extractSession(token).getUser();

        assertThat(sessionUser.getId()).isEqualTo(user.getId());
        assertThat(sessionUser.getEmail()).isEqualTo(user.getEmail());
        assertThat(sessionUser.getFirstname()).isEqualTo(user.getFirstname());
        assertThat(sessionUser.getLastname()).isEqualTo(user.getLastname());
        assertThat(sessionUser.getRole()).isEqualTo(user.getRole());
        assertThat(sessionUser.getAuthProvider()).isEqualTo(user.getAuthProvider());
        assertThat(sessionUser.getAureonVoiceId()).isEqualTo(user.getAureonVoiceId());
        assertThat(sessionUser.getPassword()).isNull();
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
