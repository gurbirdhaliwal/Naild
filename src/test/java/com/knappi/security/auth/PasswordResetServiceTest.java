package com.knappi.security.auth;

import com.knappi.security.mail.AccountEmailService;
import com.knappi.security.user.Role;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountEmailService accountEmailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "passwordResetBaseUrl", "http://localhost:3000/reset-password");
        ReflectionTestUtils.setField(passwordResetService, "passwordResetExpiryMinutes", 60L);
    }

    @Test
    void requestPasswordResetStoresHashedTokenAndSendsEmail() {
        User user = localUser();
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<User> emailedUserCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expiryCaptor = ArgumentCaptor.forClass(Long.class);

        verify(userRepository).save(userCaptor.capture());
        verify(accountEmailService).sendPasswordResetEmail(emailedUserCaptor.capture(), linkCaptor.capture(), expiryCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(emailedUserCaptor.getValue()).isSameAs(savedUser);
        assertThat(savedUser.getPasswordResetTokenHash()).hasSize(64);
        assertThat(savedUser.getPasswordResetTokenExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(59));
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:3000/reset-password?token=");
        assertThat(expiryCaptor.getValue()).isEqualTo(60L);
    }

    @Test
    void requestPasswordResetDoesNothingForUnknownEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");

        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset(request);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verify(accountEmailService, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.any(), anyString(), anyLong());
    }

    @Test
    void resetPasswordUpdatesPasswordClearsTokenAndSendsNotification() throws Exception {
        String rawToken = "reset-token";
        User user = localUser();
        user.setPasswordResetTokenHash(hash(rawToken));
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(rawToken);
        request.setPassword("new-password");

        when(userRepository.findByPasswordResetTokenHash(hash(rawToken))).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        passwordResetService.resetPassword(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(accountEmailService).sendPasswordChangedEmail(userCaptor.getValue());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getPasswordChangedAt()).isNotNull();
        assertThat(savedUser.getPasswordResetTokenHash()).isNull();
        assertThat(savedUser.getPasswordResetTokenExpiresAt()).isNull();
    }

    @Test
    void resetPasswordRejectsExpiredToken() throws Exception {
        String rawToken = "expired-token";
        User user = localUser();
        user.setPasswordResetTokenHash(hash(rawToken));
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(rawToken);
        request.setPassword("new-password");

        when(userRepository.findByPasswordResetTokenHash(hash(rawToken))).thenReturn(Optional.of(user));

        assertThrows(PasswordResetException.class, () -> passwordResetService.resetPassword(request));

        verify(userRepository).save(user);
        verify(accountEmailService, never()).sendPasswordChangedEmail(org.mockito.ArgumentMatchers.any());
    }

    private User localUser() {
        return User.builder()
                .id(1L)
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("encoded-old-password")
                .role(Role.USER)
                .authProvider("LOCAL")
                .build();
    }

    private String hash(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
